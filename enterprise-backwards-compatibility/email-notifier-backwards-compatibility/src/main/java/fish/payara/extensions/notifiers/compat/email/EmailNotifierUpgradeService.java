/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.extensions.notifiers.compat.email;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.util.StringUtils;
import fish.payara.extensions.notifiers.compat.BaseNotifierUpgradeService;
import fish.payara.extensions.notifiers.email.EmailNotifierConfiguration;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import java.util.logging.Level;

/**
 * Service that upgrades legacy config on server start.
 *
 * @author Andrew Pielage
 */
@Service
@RunLevel(StartupRunLevel.VAL)
public class EmailNotifierUpgradeService extends BaseNotifierUpgradeService {

    private static final String notifierName = "email-notifier";

    @Override
    public void postConstruct() {
        for (Config config : configs.getConfig()) {
            // First up, get the notifier configuration for each config
            NotificationServiceConfiguration notificationServiceConfiguration = config.getExtensionByType(
                    NotificationServiceConfiguration.class);
            // If there is no notifier configuration whatsoever - just exit out. This **should** be an edge case -
            // default config tags for the notification service have existed for a long time
            if (notificationServiceConfiguration == null) {
                continue;
            }

            // Next up, get the email notifier configuration for this config, creating default config tags if required
            EmailNotifierConfiguration emailNotifierConfiguration = getNotifierConfiguration(
                    notificationServiceConfiguration, EmailNotifierConfiguration.class);
            // If we don't find the config or fail to create one, exit out - something has gone fundamentally wrong
            if (emailNotifierConfiguration == null) {
                logger.log(Level.WARNING,
                        "Failed to upgrade legacy notifier configuration, could not find or create config");
                return;
            }

            // Upgrade the email notifier configuration itself
            upgradeNotifierService(emailNotifierConfiguration);

            // Upgrade each of the services that publish to notifiers
            upgradeRequestTracingService(config, notifierName, EmailNotifier.class);
            upgradeMonitoringService(config, notifierName, EmailNotifier.class);
            upgradeHealthCheckService(config, notifierName, EmailNotifier.class);
            upgradeAdminAuditService(config, notifierName, EmailNotifier.class);
        }
    }

    private void upgradeNotifierService(EmailNotifierConfiguration emailNotifierConfiguration) {
        // Get the attributes to upgrade
        String to = emailNotifierConfiguration.getTo();
        String recipient = emailNotifierConfiguration.getRecipient();

        if (StringUtils.ok(to)) {
            // If we're not overriding anything, upgrade the existing property
            if (!StringUtils.ok(recipient)) {
                try {
                    ConfigSupport.apply(emailNotifierConfigurationProxy -> {
                        emailNotifierConfigurationProxy.setRecipient(to);
                        return emailNotifierConfigurationProxy;
                    }, emailNotifierConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING,
                            "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }

            // Finally, remove the deprecated property (more accurately set it to empty)
            try {
                ConfigSupport.apply(emailNotifierConfigurationProxy -> {
                    emailNotifierConfigurationProxy.setTo("");
                    return emailNotifierConfigurationProxy;
                }, emailNotifierConfiguration);
            } catch (TransactionFailure transactionFailure) {
                logger.log(Level.WARNING,
                        "Failed to remove legacy notifier configuration", transactionFailure);
            }
        }
    }

}
