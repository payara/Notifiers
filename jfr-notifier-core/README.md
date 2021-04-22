# Flight Recorder Notifier

This module contains the core of The Java Flight Recorder Notifier. It propagates the Notification Events to the Flight Recorder Subsystem of the JVM.

It requires JDK 11 (or Zulu JDK 8 with the Flight Recorder backported) and the changes to the `osgi.properties` file as described in https://github.com/payara/Payara/pull/5180 (available in 5.2021.3 by default)

Adding the jar file to the `modules` directory of Payara Server makes
- a notifier called `jfr-notifier` available as notification channel for Health Checks, Request Tracing and Asadmin Audit.
- defines the `set-jfr-notifier-configuration` Asadmin CLI command to activate and configure the notifier.
- defines the `get-jfr-notifier-configuration` Asadmin CLI command to show the notifier configuration.

The notifier has 1 configuration parameter (`filterNames`) that can be used to 'filter' some notification events.  By default, the value contains `*` and all events are sent to the Flight Recorder. It can contain a comma-separated list of the Health check names that need to be sent like `CONP,STUCK`  (Connection Pool and Stuck Threads health check).

The Web Console plugin is available within module `jfr-notifier-console-plugin`.