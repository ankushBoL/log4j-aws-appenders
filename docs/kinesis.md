# Kinesis

The Kinesis Streams appender is intended to be an entry point for log analytics, either
as a direct feed to an analytics application, or via Kinesis Firehose to ElasticSearch
or other destinations (note that this can also be an easy way to back-up logs to S3).

The Kinesis appender provides the following features:

* Configurable destination stream, with substitution variables to specify stream name.
* Auto-creation of streams, with configurable number of shards.
* JSON messages (via [JsonLayout](jsonlayout.md)).
* Configurable discard in case of network connectivity issues.


## Configuration

The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`streamName`        | The name of the Kinesis stream that will receive messages; may use [substitutions](substitutions.md). No default value.
`partitionKey`      | A string used to assign messages to shards; see below for more information.
`autoCreate`        | If present and "true", the stream will be created if it does not already exist.
`shardCount`        | When creating a stream, specifies the number of shards to use. Defaults to 1.
`retentionPeriod`   | When creating a stream, specifies the retention period for messages in hours. Per AWS, the minimum is 24 (the default) and the maximum is 168 (7 days). Note that increasing retention time increases the per-hour shard cost.
`batchDelay`        | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See the [design doc](design.md#message-batches) for more information.
`discardThreshold`  | The threshold count for discarding messages; default is 10,000. See [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`clientFactory`     | Specifies the fully-qualified name of a static method that will be used to create the AWS service client via reflection. See [service client doc](service-client.md) for more information.
`clientEndpoint`    | Specifies a non-default endpoint for the client (eg, "logs.us-west-2.amazonaws.com"). See [service client doc](service-client.md) for more information.


### Example

```
log4j.rootLogger=INFO, kinesis

log4j.appender.kinesis=com.kdgregory.log4j.aws.KinesisAppender
log4j.appender.kinesis.streamName=logging-stream
log4j.appender.kinesis.partitionKey={pid}
log4j.appender.kinesis.batchDelay=100

log4j.appender.kinesis.layout=com.kdgregory.log4j.aws.JsonLayout
log4j.appender.kinesis.layout.tags=applicationName={env:APP_NAME}
log4j.appender.kinesis.layout.enableHostname=true
log4j.appender.kinesis.layout.enableLocation=true
```


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `kinesis:DescribeStream`
* `kinesis:PutRecords`

To auto-create a stream you must grant these additional permissions:

* `kinesis:CreateStream`
* `kinesis:IncreaseStreamRetentionPeriod`


## Stream management

You will normally pre-create the Kinesis stream, and adjust its retention period and number of
shards based on your environment.

To support testing (and because it was the original behavior, copied from the CloudWatch appender),
you can optionally configure the appender to create the stream if it does not already exist. Unlike
the CloudWatch appender, if you delete the stream during use it will not be re-created even if you
specify this parameter.


## Partition Keys

Kinesis supports high-performance parallel writes via multiple shards per stream: each shard
can accept up to 1,000 records and/or 1 MB of data per second. To distribute data between
shards, Kinesis requires each record to have a partition key, and hashes that partition key
to determine which shard is used to store the record.

The Kinesis appender allows you to set an explicit partition key, which is applied to all
messages generated by a single appender. By default this key is the application startup
timestamp, which means that all messages from that application will go to the same shard.
In most cases this is sufficient, and there's no reason to configure this parameter.

If, however, you have an application that generates a high volume of log messages, you can
get improved performance (or at least reduce the likelihood of throttling) by generating a
per-record random partition key (note that you will also need to have multiple shards in
the stream). Enable this for the appender by explicitly configuring a blank partition key:

    log4j.appender.kinesis.partitionKey=

If you have many applications logging to the same stream this may be counter-productive,
as it means that every application will consume capacity from every shard.
