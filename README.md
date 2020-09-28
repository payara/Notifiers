# Payara Notifiers

This repository contains all of the notifiers for the Payara Server notification service that are not included in the Payara Community release. They can all be built and deployed to Payara Server Community Edition to add that functionality to the server.

Each notifier consists of 2 parts:

- notifier-core
- notifier-console-plugin

The first module is the core of the notifier. This implements its functionality, and defines any configuration is requires. This can add the asadmin commands to manage the notifier, and will allow any Payara Services that make use of the notifiers to send notifications to it.

The second module integrates a notifier into the Administration Console. This module is not essential, but will allow the notifier to be managed from the admin console. Without this module deployed, the notifier name will still appear as available notifiers under the services such as request tracing, it just won't have it's own tab for configuration in the notification section.

## Installing Notifiers

To install a notifier to Payara Server Community Edition, build the relevant module and drop it into `${PAYARA_HOME}/glassfish/modules` and restart the server. If you have installed a notifier core module, you should find the related asadmin commands and parameters installed. If you have installed a console plugin, you should see a tab in the admin console under the notification service.

Note that if the notifier has any extra dependencies not provided by Payara Server, the dependency JARs also need copying into the `modules` directory. You should find that the dependency is copied into the `target` directory by the `maven-dependency-plugin`. If you don't, make sure your `pom.xml` implements the `maven-dependency-plugin` with the parent pom configuration.

## Writing New Notifiers

Custom notifiers can also be built to extend the functionality of the notification service. To implement a new notifier `core` and `console-plugin`, see the [example-notifier-core](./example-notifier-core) and [example-notifier-console-plugin](./example-notifier-console-plugin) modules respectively. You can copy these modules and refactor them to implement your own unique notifiers.

If you implement a new notifier, please contribute to this repository so that people can use the notifiers you've written!
