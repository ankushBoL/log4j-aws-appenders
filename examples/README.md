# Examples

This directory contains several examples that show how the appenders are configured:

* [log4j1-example](log4j1-example): writes logging messages to each of the destinations.
* [log4j1-webapp](log4j1-webapp): a web-app that demonstrates how to initialize and shut
  down the logging system with a `ContextListener`, along with adding a unique request
  token to the mapped diagnostic context to track each request's progress.

Each example contains its own README that goes into further detail on its operation.

In addition, there are [CloudFormation templates](cloudformation) to create the destinations
used by these examples.
