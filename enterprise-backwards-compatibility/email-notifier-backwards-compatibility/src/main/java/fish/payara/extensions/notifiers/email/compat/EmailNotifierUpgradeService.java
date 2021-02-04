package fish.payara.extensions.notifiers.email.compat;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Configs;
import com.sun.enterprise.util.StringUtils;
import fish.payara.extensions.notifiers.email.EmailNotifierConfiguration;
import fish.payara.internal.notification.admin.NotificationServiceConfiguration;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.api.admin.config.ConfigurationUpgrade;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RunLevel(StartupRunLevel.VAL)
public class EmailNotifierUpgradeService implements ConfigurationUpgrade, PostConstruct {

    @Inject
    Configs configs;

    @Override
    public void postConstruct() {
        for (Config config : configs.getConfig()) {
            NotificationServiceConfiguration notificationServiceConfiguration = config.getExtensionByType(
                    NotificationServiceConfiguration.class);
            if (notificationServiceConfiguration == null){
                continue;
            }

            EmailNotifierConfiguration emailNotifierConfiguration =
                    notificationServiceConfiguration.getNotifierConfigurationByType(EmailNotifierConfiguration.class);
            if (emailNotifierConfiguration == null){
                continue;
            }

            String to = emailNotifierConfiguration.getTo();
            String recipient = emailNotifierConfiguration.getRecipient();

            // Don't override an existing recipient
            if (StringUtils.ok(to) && !StringUtils.ok(recipient)) {
                try {
                    ConfigSupport.apply(cProxy -> {
                        cProxy.setRecipient(to);
                        cProxy.setTo("");
                        return cProxy;
                    }, emailNotifierConfiguration);
                } catch (TransactionFailure transactionFailure) {
                    Logger.getLogger(EmailNotifierUpgradeService.class.getName()).log(Level.WARNING,
                            "Failed to upgrade legacy notifier configuration", transactionFailure);
                }
            }
        }
    }

}
