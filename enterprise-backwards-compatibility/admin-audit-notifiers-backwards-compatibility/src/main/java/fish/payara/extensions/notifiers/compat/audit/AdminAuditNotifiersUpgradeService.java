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

package fish.payara.extensions.notifiers.compat.audit;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Configs;
import com.sun.enterprise.util.StringUtils;
import fish.payara.audit.AdminAuditConfiguration;
import fish.payara.audit.AdminAuditService;
import fish.payara.extensions.notifiers.compat.LegacyNotifierUpgradeService;
import fish.payara.extensions.notifiers.compat.config.Notifier;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.internal.api.Globals;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import javax.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that upgrades legacy {@link NotificationServiceConfiguration} on server start for the
 * {@link AdminAuditService}. Split out into its own module and service since Micro doesn't have this service present.
 *
 * @author Andrew Pielage
 */
@Service
@RunLevel(StartupRunLevel.VAL)
public class AdminAuditNotifiersUpgradeService implements PostConstruct {

    @Inject
    private Configs configs;

    @Inject
    private Logger logger;

    @Inject
    private ServiceLocator serviceLocator;

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

            upgradeAdminAuditService(config);
        }
    }

    /**
     * Upgrades any configured legacy notifiers for a given {@link AdminAuditConfiguration}, before removing
     * the legacy config. If there is existing "upgraded" config, it does not override it.
     * <p>
     * Split out from {@link LegacyNotifierUpgradeService} since Micro doesn't have the admin audit service
     * and so runs into ClassNotFoundExceptions.
     *
     * @param config The {@link Config} to upgrade the {@link AdminAuditConfiguration} of.
     */
    private void upgradeAdminAuditService(Config config) {
        AdminAuditConfiguration adminAuditConfiguration = config.getExtensionByType(
                AdminAuditConfiguration.class);

        if (adminAuditConfiguration == null) {
            logger.log(Level.WARNING, "Could not find admin audit service configuration to upgrade for config: {0}",
                    config.getName());
            return;
        }

        if (serviceLocator == null) {
            serviceLocator = Globals.getDefaultBaseServiceLocator();
            if (serviceLocator == null) {
                logger.log(Level.WARNING, "Could not find service locator to upgrade admin audit service. " +
                        "Notifiers for this service may behave unexpectedly");
                return;
            }
        }

        // For each legacy notifier in the admin audit configuration, upgrade it
        for (Notifier notifier : adminAuditConfiguration.getLegacyNotifierList()) {
            // Find all of the notifier upgrade services by searching for the contract
            List<LegacyNotifierUpgradeService> legacyNotifierUpgradeServices = serviceLocator.getAllServices(
                    LegacyNotifierUpgradeService.class);

            // Search through the services until we find the upgrade service that matches what we're trying to upgrade
            for (LegacyNotifierUpgradeService legacyNotifierUpgradeService : legacyNotifierUpgradeServices) {
                // Since we're working with a ConfigBeanProxy we can't simply do notifier.getClass() since this would
                // return the proxy class. Instead, we can grab the interfaces of this proxy to what's actually being
                // proxied. In this case, each ConfigBeanProxy *should* only only have a single interface: the notifier
                // config bean interface that we're trying to get (e.g. EmailNotifier) for our comparison
                if (legacyNotifierUpgradeService.getUpgradeNotifierClass().equals(
                        notifier.getClass().getInterfaces()[0])) {
                    upgradeAdminAuditService(adminAuditConfiguration, notifier,
                            legacyNotifierUpgradeService.getNewNotifierName());
                    break;
                }
            }
        }
    }

    /**
     * Upgrades a legacy notifier of the given type for a given {@link AdminAuditConfiguration} before removing the
     * legacy config. If there is existing "upgraded" config, it does not override it.
     * <p>
     * Split out from {@link LegacyNotifierUpgradeService} since Micro doesn't have the admin audit service
     * and so runs into ClassNotFoundExceptions.
     *
     * @param adminAuditConfiguration The {@link AdminAuditConfiguration} to upgrade
     * @param notifierName            The name of the notifier, corresponding to its XML tag in the domain.xml
     * @param <T>                     The type of legacy {@link Notifier} to upgrade
     */
    private <T extends Notifier> void upgradeAdminAuditService(AdminAuditConfiguration adminAuditConfiguration,
            T notifier, String notifierName) {

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.valueOf(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = adminAuditConfiguration.getNotifierList();

            if (!notifiers.contains(notifierName)) {
                // No existing config to override, so let's migrate old to new
                try {
                    ConfigSupport.apply(adminAuditConfigurationProxy -> {
                        adminAuditConfigurationProxy.getNotifierList().add(notifierName);
                        return adminAuditConfigurationProxy;
                    }, adminAuditConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
            // And finally, delete the legacy config
            try {
                ConfigSupport.apply(adminAuditConfigurationProxy -> {
                    adminAuditConfigurationProxy.getLegacyNotifierList().remove(notifier);
                    return adminAuditConfigurationProxy;
                }, adminAuditConfiguration);
            } catch (TransactionFailure transactionFailure) {
                logger.log(Level.WARNING, "Failed to remove legacy notifier configuration", transactionFailure);
            }
        }
    }

}