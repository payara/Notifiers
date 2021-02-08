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

package fish.payara.extensions.notifiers.compat;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Configs;
import com.sun.enterprise.util.StringUtils;
import fish.payara.internal.notification.PayaraNotifierConfiguration;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import fish.payara.jmx.monitoring.configuration.MonitoringServiceConfiguration;
import fish.payara.nucleus.healthcheck.configuration.HealthCheckServiceConfiguration;
import fish.payara.nucleus.requesttracing.configuration.RequestTracingServiceConfiguration;
import org.glassfish.api.admin.config.ConfigurationUpgrade;
import org.glassfish.hk2.api.PostConstruct;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import javax.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseNotifierUpgradeService implements ConfigurationUpgrade, PostConstruct {

    @Inject
    protected Configs configs;

    @Inject
    protected Logger logger;

    protected <T extends PayaraNotifierConfiguration> T getNotifierConfiguration(
            NotificationServiceConfiguration notificationServiceConfiguration, Class<T> notifierConfigurationClass) {
        T notifierConfiguration = notificationServiceConfiguration.getNotifierConfigurationByType(
                notifierConfigurationClass);
        // Will be null if no default config tags present
        if (notifierConfiguration == null) {
            try {
                // Create a transaction around the notifier config we want to add the element to, grab it's list
                // of notifiers, and add a new child element to it
                ConfigSupport.apply(notificationServiceConfigurationProxy -> {
                    notificationServiceConfigurationProxy.getNotifierConfigurationList().add(
                            notificationServiceConfigurationProxy.createChild(notifierConfigurationClass));
                    return notificationServiceConfigurationProxy;
                }, notificationServiceConfiguration);
            } catch (TransactionFailure transactionFailure) {
                logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                return null;
            }

            // Get the newly created config element
            notifierConfiguration = notificationServiceConfiguration.getNotifierConfigurationByType(
                    notifierConfigurationClass);
        }

        // Could still be null!
        return notifierConfiguration;
    }

    protected <T extends Notifier> void upgradeRequestTracingService(Config config, String notifierName,
            Class<T> notifierClass) {
        RequestTracingServiceConfiguration requestTracingServiceConfiguration = config.getExtensionByType(
                RequestTracingServiceConfiguration.class);

        if (requestTracingServiceConfiguration == null) {
            logger.log(Level.WARNING, "Could not find request tracing configuration to upgrade for config: {0}",
                    config.getName());
            return;
        }

        T notifier = requestTracingServiceConfiguration.getLegacyNotifierByType(notifierClass);
        if (notifier == null) {
            // If no config found, nothing to do!
            return;
        }

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.getBoolean(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = requestTracingServiceConfiguration.getNotifierList();
            if (!notifiers.contains(notifierName)) {
                try {
                    ConfigSupport.apply(rProxy -> {
                        // No existing config to override, so let's enable it
                        requestTracingServiceConfiguration.getNotifierList().add(notifierName);
                        // And finally, delete the legacy config
                        requestTracingServiceConfiguration.getLegacyNotifierList().remove(notifier);
                        return rProxy;
                    }, requestTracingServiceConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
        }
    }

    protected <T extends Notifier> void upgradeMonitoringService(Config config, String notifierName,
            Class<T> notifierClass) {
        MonitoringServiceConfiguration monitoringServiceConfiguration = config.getExtensionByType(
                MonitoringServiceConfiguration.class);

        if (monitoringServiceConfiguration == null) {
            logger.log(Level.WARNING, "Could not find monitoring configuration to upgrade for config: {0}",
                    config.getName());
            return;
        }

        T notifier = monitoringServiceConfiguration.getLegacyNotifierByType(notifierClass);
        if (notifier == null) {
            // If no config found, nothing to do!
            return;
        }

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.getBoolean(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = monitoringServiceConfiguration.getNotifierList();
            if (!notifiers.contains(notifierName)) {
                try {
                    ConfigSupport.apply(rProxy -> {
                        // No existing config to override, so let's enable it
                        monitoringServiceConfiguration.getNotifierList().add(notifierName);
                        // And finally, delete the legacy config
                        monitoringServiceConfiguration.getLegacyNotifierList().remove(notifier);
                        return rProxy;
                    }, monitoringServiceConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
        }
    }

    protected <T extends Notifier> void upgradeHealthCheckService(Config config, String notifierName,
            Class<T> notifierClass) {
        HealthCheckServiceConfiguration healthCheckServiceConfiguration = config.getExtensionByType(
                HealthCheckServiceConfiguration.class);

        if (healthCheckServiceConfiguration == null) {
            logger.log(Level.WARNING, "Could not find health check service configuration to upgrade for config: {0}",
                    config.getName());
            return;
        }

        T notifier = healthCheckServiceConfiguration.getLegacyNotifierByType(notifierClass);
        if (notifier == null) {
            // If no config found, nothing to do!
            return;
        }

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.getBoolean(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = healthCheckServiceConfiguration.getNotifierList();
            if (!notifiers.contains(notifierName)) {
                try {
                    ConfigSupport.apply(rProxy -> {
                        // No existing config to override, so let's enable it
                        healthCheckServiceConfiguration.getNotifierList().add(notifierName);
                        // And finally, delete the legacy config
                        healthCheckServiceConfiguration.getLegacyNotifierList().remove(notifier);
                        return rProxy;
                    }, healthCheckServiceConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
        }
    }

}
