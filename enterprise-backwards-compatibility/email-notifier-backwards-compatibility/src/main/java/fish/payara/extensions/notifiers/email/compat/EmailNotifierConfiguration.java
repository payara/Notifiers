package fish.payara.extensions.notifiers.email.compat;

import org.glassfish.api.admin.config.ConfigExtension;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfiguredBy;

import java.beans.PropertyVetoException;

@ConfiguredBy(fish.payara.extensions.notifiers.email.EmailNotifierConfiguration.class)
public interface EmailNotifierConfiguration extends ConfigExtension {

    @Deprecated
    @Attribute
    String getTo();
    void setTo(String value) throws PropertyVetoException;

}
