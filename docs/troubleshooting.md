# When Things Go Wrong

If you don't see logging output, it is almost always due to incorrect IAM permissions. The documentation
for each appender lists the permissions that you will need to use that appender. It's also possible, when
using the Kinesis and SNS appenders, that the destination doesn't exist (or is in another region).

Fortunately, both logging frameworks support debug output, and the appenders report both successful and
non-successful operation.


## Configuration

### Log4J 1.x

For Log4J, you set the system property `log4j.configDebug` to "true". The easiest way to do this is when
starting your Java application:

```
java -Dlog4j.configDebug=true ...
```

The Log4J internal logger always writes output to standard error; if you're running your program as a
daemon you will need to redirect this output.

### Logback

For Logback, you set debug mode in the configuration file:

```
<configuration debug="true">

    <!-- configuration omitted -->

</configuration>
```


## Examples

The following examples are from the Log4J [example program](../log4j1-example), invoked with the
following command:

```
```

### Successful configuration


### Missing destination


### Incorrect permissions
