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
import fish.payara.audit.AdminAuditConfiguration;
import fish.payara.extensions.notifiers.compat.config.Notifier;
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

/**
 * Base class that the notifier upgrade services pull from, containing generic methods shared by all.
 *
 * @author Andrew Pielage
 */
public abstract class BaseNotifierUpgradeService implements ConfigurationUpgrade, PostConstruct {

    @Inject
    protected Configs configs;

    @Inject
    protected Logger logger;

    /**
     * Retrieves the {@link PayaraNotifierConfiguration} for a given {@link NotificationServiceConfiguration}.
     *
     * @param notificationServiceConfiguration The {@link NotificationServiceConfiguration} to get config from
     * @param notifierConfigurationClass       The {@link PayaraNotifierConfiguration} to get
     * @param <T>                              The type of {@link PayaraNotifierConfiguration} to get
     * @return The {@link PayaraNotifierConfiguration} for the specified {@link NotificationServiceConfiguration}, or
     * null if it can't be found or created
     */
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

    /**
     * Upgrades any configured legacy notifiers for a given {@link RequestTracingServiceConfiguration}, before removing
     * the legacy config. If there is existing "upgraded" config, it does not override it.
     *
     * @param config        The {@link Config} to upgrade the {@link RequestTracingServiceConfiguration} of.
     * @param notifierName  The name of the notifier, corresponding to its XML tag in the domain.xml
     * @param notifierClass The legacy {@link Notifier} to upgrade
     * @param <T>           The type of legacy {@link Notifier} to upgrade
     */
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

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.valueOf(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = requestTracingServiceConfiguration.getNotifierList();
            if (!notifiers.contains(notifierName)) {
                // No existing config to override, so let's migrate old to new
                try {
                    ConfigSupport.apply(requestTracingServiceConfigurationProxy -> {
                        requestTracingServiceConfigurationProxy.getNotifierList().add(notifierName);
                        return requestTracingServiceConfigurationProxy;
                    }, requestTracingServiceConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
            // And finally, delete the legacy config
            try {
                ConfigSupport.apply(requestTracingServiceConfigurationProxy -> {
                    requestTracingServiceConfigurationProxy.getLegacyNotifierList().remove(notifier);
                    return requestTracingServiceConfigurationProxy;
                }, requestTracingServiceConfiguration);
            } catch (TransactionFailure transactionFailure) {
                logger.log(Level.WARNING, "Failed to remove legacy notifier configuration", transactionFailure);
            }
        }
    }

    /**
     * Upgrades any configured legacy notifiers for a given {@link MonitoringServiceConfiguration}, before removing
     * the legacy config. If there is existing "upgraded" config, it does not override it.
     *
     * @param config        The {@link Config} to upgrade the {@link MonitoringServiceConfiguration} of.
     * @param notifierName  The name of the notifier, corresponding to its XML tag in the domain.xml
     * @param notifierClass The legacy {@link Notifier} to upgrade
     * @param <T>           The type of legacy {@link Notifier} to upgrade
     */
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

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.valueOf(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = monitoringServiceConfiguration.getNotifierList();
            if (!notifiers.contains(notifierName)) {
                // No existing config to override, so let's migrate old to new
                try {
                    ConfigSupport.apply(monitoringServiceConfigurationProxy -> {
                        monitoringServiceConfigurationProxy.getNotifierList().add(notifierName);
                        return monitoringServiceConfigurationProxy;
                    }, monitoringServiceConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
            // And finally, delete the legacy config
            try {
                ConfigSupport.apply(monitoringServiceConfigurationProxy -> {
                    monitoringServiceConfigurationProxy.getLegacyNotifierList().remove(notifier);
                    return monitoringServiceConfigurationProxy;
                }, monitoringServiceConfiguration);
            } catch (TransactionFailure transactionFailure) {
                logger.log(Level.WARNING, "Failed to remove legacy notifier configuration", transactionFailure);
            }
        }
    }

    /**
     * Upgrades any configured legacy notifiers for a given {@link HealthCheckServiceConfiguration}, before removing
     * the legacy config. If there is existing "upgraded" config, it does not override it.
     *
     * @param config        The {@link Config} to upgrade the {@link HealthCheckServiceConfiguration} of.
     * @param notifierName  The name of the notifier, corresponding to its XML tag in the domain.xml
     * @param notifierClass The legacy {@link Notifier} to upgrade
     * @param <T>           The type of legacy {@link Notifier} to upgrade
     */
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

        if (StringUtils.ok(notifier.getEnabled()) && Boolean.valueOf(notifier.getEnabled())) {
            // Since this is enabled, there might be something for us to do.
            // Get the new notifier config list to see if this notifier is enabled - we don't want to override an
            // existing value
            List<String> notifiers = healthCheckServiceConfiguration.getNotifierList();
            if (!notifiers.contains(notifierName)) {
                // No existing config to override, so let's migrate old to new
                try {
                    ConfigSupport.apply(healthCheckServiceConfigurationProxy -> {
                        healthCheckServiceConfigurationProxy.getNotifierList().add(notifierName);
                        return healthCheckServiceConfigurationProxy;
                    }, healthCheckServiceConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    logger.log(Level.WARNING, "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
            // And finally, delete the legacy config
            try {
                ConfigSupport.apply(healthCheckServiceConfigurationProxy -> {
                    healthCheckServiceConfigurationProxy.getLegacyNotifierList().remove(notifier);
                    return healthCheckServiceConfigurationProxy;
                }, healthCheckServiceConfiguration);
            } catch (TransactionFailure transactionFailure) {
                logger.log(Level.WARNING, "Failed to remove legacy notifier configuration", transactionFailure);
            }
        }
    }

    /**
     * Upgrades any configured legacy notifiers for a given {@link AdminAuditConfiguration}, before removing
     * the legacy config. If there is existing "upgraded" config, it does not override it.
     *
     * @param config        The {@link Config} to upgrade the {@link AdminAuditConfiguration} of.
     * @param notifierName  The name of the notifier, corresponding to its XML tag in the domain.xml
     * @param notifierClass The legacy {@link Notifier} to upgrade
     * @param <T>           The type of legacy {@link Notifier} to upgrade
     */
    protected <T extends Notifier> void upgradeAdminAuditService(Config config, String notifierName,
            Class<T> notifierClass) {
        AdminAuditConfiguration adminAuditConfiguration = config.getExtensionByType(
                AdminAuditConfiguration.class);

        if (adminAuditConfiguration == null) {
            logger.log(Level.WARNING, "Could not find admin audit service configuration to upgrade for config: {0}",
                    config.getName());
            return;
        }

        T notifier = adminAuditConfiguration.getLegacyNotifierByType(notifierClass);
        if (notifier == null) {
            // If no config found, nothing to do!
            return;
        }

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
