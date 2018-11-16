# Examples

This directory contains several examples that show how the appenders are configured:

* [log4j1-example](log4j1-example): writes logging messages to each of the destinations.
* [log4j1-webapp](log4j1-webapp): a simple web-app that demonstrates how to use a
  `ContextListener` to initialize and shut down the logging system.
* [logback-example](logback-example): writes logging messages to each of the destinations.
* [logback-webapp](logback-webapp): a web-app that uses `JsonLayout` and `JsonAccessLayout`
  to write correlated log messages, along with the Logback `ContextListener` to properly
  shut down the logging framework when the application is undeployed.

Each example contains its own README that goes into further detail on its operation.

In addition, there are [CloudFormation templates](cloudformation) to create the destinations
used by these examples.
