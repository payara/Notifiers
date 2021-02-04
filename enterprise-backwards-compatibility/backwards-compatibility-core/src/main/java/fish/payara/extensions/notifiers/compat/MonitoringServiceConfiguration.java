package fish.payara.extensions.notifiers.compat;

import org.glassfish.api.admin.config.ConfigExtension;
import org.jvnet.hk2.config.ConfiguredBy;
import org.jvnet.hk2.config.DuckTyped;
import org.jvnet.hk2.config.Element;

@ConfiguredBy(fish.payara.jmx.monitoring.configuration.MonitoringServiceConfiguration.class)
public interface MonitoringServiceConfiguration extends ConfigExtension {

//    @Deprecated
//    @Element("*")
//    List<Notifier> getNotifierList();
//
//    /**
//     * Gets a specific notifier
//     * @since 4.1.2.174
//     * @param <T>
//     * @param type The class name of the notifier to get
//     * @return
//     */
//    @DuckTyped
//    <T extends Notifier> T getNotifierByType(Class type);
//
//    class Duck {
//
//        public static <T extends Notifier> T getNotifierByType(MonitoringServiceConfiguration config, Class<T> type) {
//            for (Notifier notifier : config.getNotifierList()) {
//                try {
//                    return type.cast(notifier);
//                } catch (Exception e) {
//                    // ignore, not the right type.
//                }
//            }
//            return null;
//        }
//
//    }


}
