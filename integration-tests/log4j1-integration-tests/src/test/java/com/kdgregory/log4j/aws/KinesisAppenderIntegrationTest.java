// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;

import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.testhelpers.KinesisTestHelper;
import com.kdgregory.logging.testhelpers.KinesisTestHelper.RetrievedRecord;


public class KinesisAppenderIntegrationTest
{
    private Logger localLogger;
    KinesisTestHelper testHelper;

    private static boolean localFactoryUsed;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        localFactoryUsed = false;
    }

//----------------------------------------------------------------------------
//  Tests
//
//  Note: most tests create their streams, since we want to examine various
//        combinations of shards and partition keys
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages = 1001;

        init("smoketest");
        localLogger.info("smoketest: starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        KinesisAppender appender = (KinesisAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketest: reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);

        testHelper.assertMessages(messages, 1, numMessages, "test");

        testHelper.assertShardCount(3);
        testHelper.assertRetentionPeriod(48);

        assertTrue("client factory used", localFactoryUsed);
        testHelper.assertStats(appender.getAppenderStatistics(), numMessages);

        localLogger.info("smoketest: finished");
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        int messagesPerThread = 500;

        init("testMultipleThreadsSingleAppender");
        localLogger.info("multi-thread/single-appender: starting");

        Logger testLogger = Logger.getLogger("TestLogger");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread)
        };

        MessageWriter.runOnThreads(writers);
        int expectedMessages = writers.length * messagesPerThread;

        localLogger.info("multi-thread/single-appender: reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(expectedMessages);

        testHelper.assertMessages(messages, writers.length, messagesPerThread * writers.length, "test");

        Map<String,List<RetrievedRecord>> groupedByShard = testHelper.groupByShard(messages);
        assertEquals("all messages written to same shard", 1, groupedByShard.size());

        testHelper.assertShardCount(2);
        testHelper.assertRetentionPeriod(24);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/single-appender: finished");
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDistinctPartitions() throws Exception
    {
        int messagesPerThread = 500;

        init("testMultipleThreadsMultipleAppendersDistinctPartitions");
        localLogger.info("multi-thread/multi-appender: starting");

        Logger testLogger1 = Logger.getLogger("TestLogger1");
        Logger testLogger2 = Logger.getLogger("TestLogger2");
        Logger testLogger3 = Logger.getLogger("TestLogger3");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(testLogger1, messagesPerThread),
            new MessageWriter(testLogger2, messagesPerThread),
            new MessageWriter(testLogger3, messagesPerThread),
            new MessageWriter(testLogger1, messagesPerThread),
            new MessageWriter(testLogger2, messagesPerThread),
            new MessageWriter(testLogger3, messagesPerThread)
        };

        MessageWriter.runOnThreads(writers);
        int expectedMessages = writers.length * messagesPerThread;

        localLogger.info("multi-thread/multi-appender: reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(expectedMessages);

        testHelper.assertMessages(messages, writers.length, messagesPerThread * 2, "test1", "test2", "test3");

        Map<String,List<RetrievedRecord>> groupedByShard = testHelper.groupByShard(messages);
        assertEquals("messages written to multiple shards", 2, groupedByShard.size());

        testHelper.assertShardCount(2);
        testHelper.assertRetentionPeriod(24);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/multi-appender: finished");
    }


    @Test
    public void testRandomPartitionKeys() throws Exception
    {
        final int numMessages = 250;

        init("testRandomPartitionKeys");
        localLogger.info("testRandomPartitionKeys: starting");

        Logger testLogger = Logger.getLogger("TestLogger");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testRandomPartitionKeys: reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);

        testHelper.assertShardCount(2);

        // at this point I'm going to assume that the message content is correct,
        // because three other tests have asserted that, so will just verify overall
        // count and how the records were partitioned

        assertEquals("overall message count", numMessages, messages.size());

        Set<String> partitionKeys = new HashSet<String>();
        for (RetrievedRecord message : messages)
        {
            partitionKeys.add(message.partitionKey);
            StringAsserts.assertRegex("8-character numeric partition key (was: " + message.partitionKey + ")",
                                      "\\d{8}", message.partitionKey);
        }
        assertTrue("expected roughly " + numMessages + " partition keys (was: " + partitionKeys.size() + ")",
                   (partitionKeys.size() > numMessages - 20) && (partitionKeys.size() < numMessages + 20));

        localLogger.info("testRandomPartitionKeys: finished");
    }


    @Test
    public void testFailsIfNoStreamPresent() throws Exception
    {
        final String streamName = "AppenderIntegrationTest-testFailsIfNoStreamPresent";
        final int numMessages = 1001;

        init("testFailsIfNoStreamPresent");
        localLogger.info("testFailsIfNoStreamPresent: starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        KinesisAppender appender = (KinesisAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testFailsIfNoStreamPresent: waiting for writer initialization to finish");

        waitForWriterInitialization(appender, 10);
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        StringAsserts.assertRegex(
            "initialization message did not indicate missing stream (was \"" + initializationMessage + "\")",
            ".*stream.*" + streamName + ".* not exist .*",
            initializationMessage);

        localLogger.info("testFailsIfNoStreamPresent: finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Logger-specific implementation of utility class.
     */
    private static class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        private Logger logger;

        public MessageWriter(Logger logger, int numMessages)
        {
            super(numMessages);
            this.logger = logger;
        }

        @Override
        protected void writeLogMessage(String message)
        {
            logger.debug(message);
        }
    }


    /**
     *  Factory method called by smoketest
     */
    public static AmazonKinesis createClient()
    {
        localFactoryUsed = true;
        return AmazonKinesisClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName)
    throws Exception
    {
        String propertiesName = "KinesisAppenderIntegrationTest/" + testName + ".properties";
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(config);

        localLogger = Logger.getLogger(getClass());

        String streamName = "AppenderIntegrationTest-" + testName;
        testHelper = new KinesisTestHelper(AmazonKinesisClientBuilder.defaultClient(), streamName);

        testHelper.deleteStreamIfExists();
    }


    /**
     *  Waits until the passed appender (1) creates a writer, and (2) that writer
     *  signals that initialization is complete or that an error occurred.
     *
     *  @return The writer's initialization message (null means successful init).
     */
    private void waitForWriterInitialization(KinesisAppender appender, int timeoutInSeconds)
    throws Exception
    {
        long timeoutAt = System.currentTimeMillis() + 1000 * timeoutInSeconds;
        while (System.currentTimeMillis() < timeoutAt)
        {
            KinesisLogWriter writer = ClassUtil.getFieldValue(appender, "writer", KinesisLogWriter.class);
            if ((writer != null) && writer.isInitializationComplete())
                return;
            else if (appender.getAppenderStatistics().getLastErrorMessage() != null)
                return;

            Thread.sleep(1000);
        }

        fail("writer not initialized within timeout");
    }
}