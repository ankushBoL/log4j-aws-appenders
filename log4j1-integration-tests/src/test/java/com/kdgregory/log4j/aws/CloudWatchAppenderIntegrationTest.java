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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.collections.CollectionUtil;
import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.ObjectUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;


public class CloudWatchAppenderIntegrationTest
{
    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGSTREAM_BASE  = "AppenderTest";

    private Logger localLogger;
    private AWSLogs localClient;

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
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final String logGroupName = "AppenderIntegrationTest-smoketest";
        final int numMessages     = 1001;
        final int rotationCount   = 333;

        setUp("CloudWatchAppenderIntegrationTest/smoketest.properties", logGroupName);
        localLogger.info("smoketest: starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        CloudWatchAppender appender = (CloudWatchAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketest: all messages written; sleeping to give writers chance to run");
        Thread.sleep(5000);

        assertMessages(logGroupName, LOGSTREAM_BASE + "-1", rotationCount);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-2", rotationCount);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-3", rotationCount);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-4", numMessages % rotationCount);

        CloudWatchWriterStatistics appenderStats = appender.getAppenderStatistics();
        assertEquals("actual log group name, from statistics",  "AppenderIntegrationTest-smoketest",    appenderStats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", LOGSTREAM_BASE + "-4",                   appenderStats.getActualLogStreamName());
        assertEquals("messages written, from statistics",       numMessages,                            appenderStats.getMessagesSent());

        CloudWatchLogWriter lastWriter = ClassUtil.getFieldValue(appender, "writer", CloudWatchLogWriter.class);
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        assertTrue("client factory used", localFactoryUsed);

        // while we're here, verify some more of the plumbing

        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());

        localLogger.info("smoketest: finished");
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        final String logGroupName   = "AppenderIntegrationTest-testMultipleThreadsSingleAppender";
        final int messagesPerThread = 200;
        final int rotationCount     = 333;

        setUp("CloudWatchAppenderIntegrationTest/testMultipleThreadsSingleAppender.properties", logGroupName);
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

        localLogger.info("multi-thread/single-appender: all threads started; sleeping to give writer chance to run");
        Thread.sleep(3000);

        assertMessages(logGroupName, LOGSTREAM_BASE + "-1", rotationCount);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-2", rotationCount);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-3", rotationCount);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-4", (messagesPerThread * writers.length) % rotationCount);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/single-appender: finished");
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDifferentDestinations() throws Exception
    {
        final String logGroupName   = "AppenderIntegrationTest-testMultipleThreadsMultipleAppendersDifferentDestinations";
        final int messagesPerThread = 1000;

        setUp("CloudWatchAppenderIntegrationTest/testMultipleThreadsMultipleAppendersDifferentDestinations.properties", logGroupName);
        localLogger.info("multi-thread/multi-appender/same destination: starting");

        MessageWriter.runOnThreads(
            new MessageWriter(Logger.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger3"), messagesPerThread));

        localLogger.info("multi-thread/multi-appender/same destination: all threads started; sleeping to give writer chance to run");
        Thread.sleep(3000);

        assertMessages(logGroupName, LOGSTREAM_BASE + "-1", messagesPerThread);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-2", messagesPerThread);
        assertMessages(logGroupName, LOGSTREAM_BASE + "-3", messagesPerThread);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/multi-appender/same destination: finished");
    }


    @Test
    public void testMultipleThreadsMultipleAppendersSameDestination() throws Exception
    {
        final String logGroupName   = "AppenderIntegrationTest-testMultipleThreadsMultipleAppendersSameDestination";
        final int messagesPerThread = 1000;

        setUp("CloudWatchAppenderIntegrationTest/testMultipleThreadsMultipleAppendersSameDestination.properties", logGroupName);
        localLogger.info("multi-thread/multi-appender/different destination: starting");

        MessageWriter.runOnThreads(
            new MessageWriter(Logger.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger3"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger4"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger5"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger3"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger4"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger5"), messagesPerThread));

        localLogger.info("multi-thread/multi-appender/different destination: all threads started; sleeping to give writer chance to run");
        Thread.sleep(20000);    // this sleep assumes that each batch will be retried once

        assertMessages(logGroupName, LOGSTREAM_BASE, messagesPerThread * 10);

        int messageCountFromStats = 0;
        int messagesDiscardedFromStats = 0;
        int raceRetriesFromStats = 0;
        int unrecoveredRaceRetriesFromStats = 0;
        String lastErrorMessage = null;
        for (int appenderNumber = 1 ; appenderNumber <= 5 ; appenderNumber++)
        {
            Logger testLogger = Logger.getLogger("TestLogger" + appenderNumber);
            CloudWatchAppender appender = (CloudWatchAppender)testLogger.getAppender("test" + appenderNumber);
            CloudWatchWriterStatistics stats = appender.getAppenderStatistics();
            messageCountFromStats += stats.getMessagesSent();
            messagesDiscardedFromStats += stats.getMessagesDiscarded();
            raceRetriesFromStats += stats.getWriterRaceRetries();
            unrecoveredRaceRetriesFromStats += stats.getUnrecoveredWriterRaceRetries();
            lastErrorMessage = ObjectUtil.defaultValue(stats.getLastErrorMessage(), lastErrorMessage);
        }

        assertEquals("stats: message count",        messagesPerThread * 10, messageCountFromStats);
        assertEquals("stats: messages discarded",   0,                      messagesDiscardedFromStats);

        // for the test to be valid, we want to see that there was at least one retry
        assertTrue("stats: race retries",                       raceRetriesFromStats > 0);
        assertEquals("stats: all race retries recovered",   0,  unrecoveredRaceRetriesFromStats);

        // we shouldn't be seeing any other errors, so fail the test if we do
        assertNull("stats: last error (was: " + lastErrorMessage + ")", lastErrorMessage);

        localLogger.info("multi-thread/multi-appender/different destination: finished");
    }


    @Test
    public void testLogstreamDeletionAndRecreation() throws Exception
    {
        final String logGroupName  = "AppenderIntegrationTest-testLogstreamDeletionAndRecreation";
        final String logStreamName = LOGSTREAM_BASE + "-1";
        final int numMessages      = 100;

        setUp("CloudWatchAppenderIntegrationTest/testLogstreamDeletionAndRecreation.properties", logGroupName);
        localLogger.info("testLogstreamDeletionAndRecreation: starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        CloudWatchAppender appender = (CloudWatchAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testLogstreamDeletionAndRecreation: first batch of messages written; sleeping to give writer chance to run");
        Thread.sleep(1000);

        assertMessages(logGroupName, logStreamName, numMessages);

        localLogger.info("testLogstreamDeletionAndRecreation: deleting stream");
        localClient.deleteLogStream(new DeleteLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName));
        boolean stillExists = true;
        for (int ii = 0 ; ii < 60 && stillExists ; ii++)
        {
            DescribeLogStreamsResult describeResult = localClient.describeLogStreams(
                                                            new DescribeLogStreamsRequest()
                                                            .withLogGroupName(logGroupName)
                                                            .withLogStreamNamePrefix(logStreamName));
            stillExists = CollectionUtil.isNotEmpty(describeResult.getLogStreams());
        }
        assertFalse("stream was removed", stillExists);

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testLogstreamDeletionAndRecreation: second batch of messages written; sleeping to give writer chance to run");
        Thread.sleep(2000);

        // the original batch of messages will be gone, so we can assert the new batch was written
        assertMessages(logGroupName, logStreamName, numMessages);

        assertTrue("statistics has error message", appender.getAppenderStatistics().getLastErrorMessage().contains("log stream missing"));

        localLogger.info("testLogstreamDeletionAndRecreation: finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  This function is used as a client factory by the smoketest.
     */
    public static AWSLogs createClient()
    {
        localFactoryUsed = true;
        return AWSLogsClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void setUp(String propertiesName, String logGroupName) throws Exception
    {
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(config);

        localLogger = Logger.getLogger(getClass());

        localClient = AWSLogsClientBuilder.defaultClient();

        deleteLogGroupIfExists(logGroupName);
    }


    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order. Properly handles multi-threaded writes.
     */
    private void assertMessages(String logGroupName, String logStreamName, int expectedMessageCount) throws Exception
    {
        LinkedHashSet<OutputLogEvent> events = retrieveAllMessages(logGroupName, logStreamName);
        assertEquals("number of events in " + logStreamName, expectedMessageCount, events.size());

        Map<Integer,Integer> lastMessageByThread = new HashMap<Integer,Integer>();
        for (OutputLogEvent event : events)
        {
            String message = event.getMessage().trim();
            Matcher matcher = MessageWriter.PATTERN.matcher(message);
            assertTrue("message matches pattern: " + message, matcher.matches());

            Integer threadNum = MessageWriter.getThreadId(matcher);
            Integer messageNum = MessageWriter.getMessageNumber(matcher);
            Integer prevMessageNum = lastMessageByThread.get(threadNum);
            if (prevMessageNum == null)
            {
                lastMessageByThread.put(threadNum, messageNum);
            }
            else
            {
                assertTrue("previous message (" + prevMessageNum + ") lower than current (" + messageNum + ")",
                           prevMessageNum.intValue() < messageNum.intValue());
            }
        }
    }


    /**
     *  Reads all messages from a stream.
     */
    private LinkedHashSet<OutputLogEvent> retrieveAllMessages(String logGroupName, String logStreamName)
    throws Exception
    {
        LinkedHashSet<OutputLogEvent> result = new LinkedHashSet<OutputLogEvent>();

        ensureLogStreamAvailable(logGroupName, logStreamName);
        GetLogEventsRequest request = new GetLogEventsRequest()
                              .withLogGroupName(logGroupName)
                              .withLogStreamName(logStreamName)
                              .withStartFromHead(Boolean.TRUE);

        // once you've read all outstanding messages, the request will return the same token
        // so we try to read at least one response, and repeat until the token doesn't change

        String prevToken = "";
        String nextToken = "";
        do
        {
            prevToken = nextToken;
            GetLogEventsResult response = localClient.getLogEvents(request);
            result.addAll(response.getEvents());
            nextToken = response.getNextForwardToken();
            request.setNextToken(nextToken);
            Thread.sleep(500);
        } while (! prevToken.equals(nextToken));

        return result;
    }


    /**
     *  Waits until the named logstream is available, throwing if it isn't available
     *  after one minute. If the log group isn't available, that's considered equal
     *  to the stream not being ready.
     */
    private void ensureLogStreamAvailable(String logGroupName, String logStreamName)
    throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            try
            {
                DescribeLogStreamsRequest reqest = new DescribeLogStreamsRequest()
                                                   .withLogGroupName(logGroupName)
                                                   .withLogStreamNamePrefix(logStreamName);
                DescribeLogStreamsResult response = localClient.describeLogStreams(reqest);
                List<LogStream> streams = response.getLogStreams();
                if ((streams != null) && (streams.size() > 0))
                {
                    return;
                }
            }
            catch (ResourceNotFoundException ignored)
            {
                // this indicates that the log group isn't available
            }
            Thread.sleep(1000);
        }
        fail("stream \"" + logGroupName + "/" + logStreamName + "\" wasn't ready within 60 seconds");
    }


    /**
     *  We leave the log group for post-mortem analysis, but want to ensure
     *  that it's gone before starting a new test.
     */
    private void deleteLogGroupIfExists(String logGroupName) throws Exception
    {
        try
        {
            localClient.deleteLogGroup(new DeleteLogGroupRequest().withLogGroupName(logGroupName));
            while (true)
            {
                DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroupName);
                DescribeLogGroupsResult response = localClient.describeLogGroups(request);
                if ((response.getLogGroups() == null) || (response.getLogGroups().size() == 0))
                {
                    return;
                }
                Thread.sleep(250);
            }
        }
        catch (ResourceNotFoundException ignored)
        {
            // this gets thrown if we deleted a non-existent log group; that's OK
        }
    }
}
