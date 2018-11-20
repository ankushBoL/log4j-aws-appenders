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

package com.kdgregory.logback.aws;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.kdgregory.logback.aws.internal.AbstractJsonLayout;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;


/**
 *  Formats an <code>IAccessEvent</code> as a JSON string, with additional parameters.
 *  <p>
 *  The JSON object always contains the following properties. Most come from the event,
 *  but some are extracted from the request itself (which is provided by the event).
 *  <ul>
 *  <li> <code>timestamp</code>:        the date/time that the message was logged.
 *  <li> <code>thread</code>:           the name of the thread where the message was logged.
 *  <li> <code>elapsedTime</code>:      milliseconds taken to process request.
 *  <li> <code>protocol</code>:
 *  <li> <code>requestMethod</code>:
 *  <li> <code>requestURI</code>:
 *  <li> <code>queryString</code>:
 *  <li> <code>remoteIp</code>:
 *  <li> <code>remotePort</code>:
 *  <li> <code>localPort</code>:
 *  <li> <code>clientHost</code>:
 *  <li> <code>user</code>:
 *  <li> <code>serverName</code>:
 *  <li> <code>sessionId</code>:
 *  <li> <code>statusCode</code>:
 *  <li> <code>bytesSent</code>:        response content length.
 *  <li> <code>processId</code>:        the PID of the invoking process, if available (this is
 *                                      retrieved from <code>RuntimeMxBean</code> and may not be
 *                                      available on all platforms).
 *  </ul>
 *  <p>
 *  The following elements are described in the documentation for <code>PatternLayout</code> but
 *  are not supported by this layout for the reasons given.
 *  <ul>
 *  <li> <code>localIP</code>:          this calls <code>InetAddress.getLocalHost()</code>, which
 *                                      just returns the loopback address. As such, I don't believe
 *                                      that it adds any value.
 *  <li> <code>requestURL</code>:       this is in fact not the request URL, but a reconstructed
 *                                      request line.     
 *  </ul>
 *  <p>
 *  The following properties are potentially expensive to compute, or significantly increase
 *  the size of the generated JSON, so will only appear if specifically enabled:
 *  <ul>
 *  <li> <code></code>:
 *  
 *  <li> <code>server</code>:           the name of the server handling the request.
 *  <li> <code>clientHost</code>:       the fully-qualified name of the remote host, if available
 *                                      (if not available, will be the remote IP address). Enable
 *                                      with <code>enableRemoteHost</code>.
 *  <li> <code>requestHeaders</code>:   a map of the request headers, where the key is the header
 *                                      name and the value is the header value. Note that multiple
 *                                      headers may be combined into a single value. The properties
 *                                      <code>includeHeaders</code> and <code>excludeHeaders</code>
 *                                      control whether headers are enabled, and which are emitted.
 *  <li> <code>responseHeaders</code>:  a map of the response headers, where the key is the header
 *                                      name and the value is the header value. Note that multiple
 *                                      headers may be combined into a single value. As with request
 *                                      headers, you explicitly control which headers are included
 *                                      or excluded.
 *  <li> <code>parameters</code>:       a map of the request parameters. By default all parameters
 *                                      are included; the <code>excludeParameters</code> option is
 *                                      used to keep parameters from being omitted (for example,
 *                                      to exclude password fields).
 *  <li> <code>instanceId</code>:       the EC2 instance ID of the machine where the logger is
 *                                      running. WARNING: do not enable this elsewhere, as the
 *                                      operation to retrieve this value may take a long time.
 *  <li> <code>hostname</code>:         the name of the machine where the logger is running, if
 *                                      available (this is currently retrieved from
 *                                      <code>RuntimeMxBean</code> and may not be available on
 *                                      all platforms).
 *  </ul>
 *  <p>
 *  Lastly, you can define a set of user tags, which are written as a child object with
 *  the key <code>tags</code>. These are intended to provide program-level information,
 *  in the case where multiple programs send their logs to the same stream. They are set
 *  as a single comma-separate string, which may contain substitution values (example:
 *  <code>appName=Fribble,startedAt={startupTimestamp}</code>).
 *  <p>
 *  WARNING: you should not rely on the order in which elements are output. Any apparent
 *  ordering is an implementation artifact and subject to change without notice.
 */
public class JsonAccessLayout
extends AbstractJsonLayout<IAccessEvent>
{

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    // TODO - add configuration for omitted secrets

//----------------------------------------------------------------------------
//  Layout Overrides
//----------------------------------------------------------------------------

    @Override
    public String doLayout(IAccessEvent event)
    {
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put("timestamp",        new Date(event.getTimeStamp()));
        map.put("thread",           event.getThreadName());
        map.put("elapsedTime",      event.getElapsedTime());
        map.put("requestURL",       event.getRequestURL());
        map.put("protocol",         event.getProtocol());
        map.put("requestMethod",    event.getMethod());
        map.put("requestURI",       event.getRequestURI());
        map.put("queryString",      event.getQueryString());
        map.put("statusCode",       event.getStatusCode());
        map.put("bytesSent",        event.getContentLength());
        
        // FIXME - this needs to be split
        map.put("remoteIp",         event.getRemoteAddr());
        map.put("remotePort",       event.getRemoteAddr());
        
        // FIXME - this should only be called if the session exists
        //         should be in ch.qos.logback.access.spi.AccessEvent but isn't
//        map.put("sessionId",        event.getSessionID());
        
        // TODO - optional parameters

//        map.put("server",           event.getServerName());
        
        // TODO - headers, parameters

        return addCommonAttributesAndConvert(map);
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

}
