package fish.payara.extensions.notifiers.compat;

import com.sun.enterprise.config.serverbeans.Configs;
import fish.payara.internal.notification.PayaraNotifierConfiguration;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.glassfish.api.admin.config.ConfigurationUpgrade;
import org.glassfish.hk2.api.PostConstruct;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import javax.inject.Inject;
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
            notifierConfiguration = notificationServiceConfiguration.getNotifierConfigurationByType(notifierConfigurationClass);
        }

        // Could still be null!
        return notifierConfiguration;
    }

}
