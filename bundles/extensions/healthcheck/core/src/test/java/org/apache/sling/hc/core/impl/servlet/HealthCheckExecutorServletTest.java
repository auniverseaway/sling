/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.sling.hc.core.impl.servlet;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.hc.api.Result;
import org.apache.sling.hc.api.Result.Status;
import org.apache.sling.hc.api.execution.HealthCheckExecutionOptions;
import org.apache.sling.hc.api.execution.HealthCheckExecutionResult;
import org.apache.sling.hc.api.execution.HealthCheckExecutor;
import org.apache.sling.hc.core.impl.executor.ExecutionResult;
import org.apache.sling.hc.util.HealthCheckMetadata;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

public class HealthCheckExecutorServletTest {

    private HealthCheckExecutorServlet healthCheckExecutorServlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HealthCheckExecutor healthCheckExecutor;

    @Mock
    private ResultHtmlSerializer htmlSerializer;

    @Mock
    private ResultJsonSerializer jsonSerializer;

    @Mock
    private ResultTxtSerializer txtSerializer;

    @Mock
    private ServiceReference hcServiceRef;

    @Mock
    private PrintWriter writer;

    @Before
    public void setup() throws IOException {

        healthCheckExecutorServlet = new HealthCheckExecutorServlet();

        MockitoAnnotations.initMocks(this);
        healthCheckExecutorServlet.healthCheckExecutor = healthCheckExecutor;
        healthCheckExecutorServlet.htmlSerializer = htmlSerializer;
        healthCheckExecutorServlet.jsonSerializer = jsonSerializer;
        healthCheckExecutorServlet.txtSerializer = txtSerializer;

        doReturn(500L).when(hcServiceRef).getProperty(Constants.SERVICE_ID);
        doReturn(writer).when(response).getWriter();
    }

    @Test
    public void testDoGetHtml() throws ServletException, IOException {

        String testTag = "testTag";
        doReturn(testTag).when(request).getParameter(HealthCheckExecutorServlet.PARAM_TAGS.name);
        doReturn("false").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.CRITICAL);
        doReturn(executionResults).when(healthCheckExecutor).execute(new HealthCheckExecutionOptions(), testTag);
        
        healthCheckExecutorServlet.doGet(request, response);

        verifyZeroInteractions(jsonSerializer);
        verifyZeroInteractions(txtSerializer);
        verify(htmlSerializer)
                .serialize(resultEquals(new Result(Result.Status.CRITICAL, "Overall Status CRITICAL")), eq(executionResults), contains("Supported URL parameters"), eq(false));
    }

    @Test
    public void testDoGetJson() throws ServletException, IOException {

        String testTag = "testTag";
        doReturn("true").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        int timeout = 5000;
        doReturn(timeout + "").when(request).getParameter(HealthCheckExecutorServlet.PARAM_OVERRIDE_GLOBAL_TIMEOUT.name);
        doReturn("/" + testTag + ".json").when(request).getPathInfo();
        List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.WARN);
        HealthCheckExecutionOptions options = new HealthCheckExecutionOptions();
        options.setCombineTagsWithOr(true);
        options.setOverrideGlobalTimeout(timeout);
        doReturn(executionResults).when(healthCheckExecutor).execute(options, testTag);

        healthCheckExecutorServlet.doGet(request, response);

        verifyZeroInteractions(htmlSerializer);
        verifyZeroInteractions(txtSerializer);
        verify(jsonSerializer).serialize(resultEquals(new Result(Result.Status.WARN, "Overall Status WARN")), eq(executionResults), anyString(),
                eq(false));

    }

    @Test
    public void testDoGetTxt() throws ServletException, IOException {

        String testTag = "testTag";
        doReturn(testTag).when(request).getParameter(HealthCheckExecutorServlet.PARAM_TAGS.name);
        doReturn("txt").when(request).getParameter(HealthCheckExecutorServlet.PARAM_FORMAT.name);
        doReturn("true").when(request).getParameter(HealthCheckExecutorServlet.PARAM_COMBINE_TAGS_WITH_OR.name);
        int timeout = 5000;
        doReturn(timeout + "").when(request).getParameter(HealthCheckExecutorServlet.PARAM_OVERRIDE_GLOBAL_TIMEOUT.name);
        List<HealthCheckExecutionResult> executionResults = getExecutionResults(Result.Status.WARN);
        HealthCheckExecutionOptions options = new HealthCheckExecutionOptions();
        options.setCombineTagsWithOr(true);
        options.setOverrideGlobalTimeout(timeout);
        doReturn(executionResults).when(healthCheckExecutor).execute(options, testTag);

        healthCheckExecutorServlet.doGet(request, response);

        verifyZeroInteractions(htmlSerializer);
        verifyZeroInteractions(jsonSerializer);
        verify(txtSerializer).serialize(resultEquals(new Result(Result.Status.WARN, "Overall Status WARN")));

    }

    private List<HealthCheckExecutionResult> getExecutionResults(Result.Status worstStatus) {
        List<HealthCheckExecutionResult> results = new ArrayList<HealthCheckExecutionResult>();
        results.add(new ExecutionResult(new HealthCheckMetadata(hcServiceRef), new Result(worstStatus, worstStatus.name()), 100));
        results.add(new ExecutionResult(new HealthCheckMetadata(hcServiceRef), new Result(Result.Status.OK, "OK"), 100));
        return results;
    }

    @Test
    public void testGetStatusMapping() throws ServletException {

        Map<Status, Integer> statusMapping = healthCheckExecutorServlet.getStatusMapping("CRITICAL:503");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 503);

        statusMapping = healthCheckExecutorServlet.getStatusMapping("OK:333");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 333);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 333);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 333);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 333);

        statusMapping = healthCheckExecutorServlet.getStatusMapping("OK:200,WARN:418,CRITICAL:503,HEALTH_CHECK_ERROR:500");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 418);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 500);

        statusMapping = healthCheckExecutorServlet.getStatusMapping("CRITICAL:503,HEALTH_CHECK_ERROR:500");
        assertEquals(statusMapping.get(Result.Status.OK), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.WARN), (Integer) 200);
        assertEquals(statusMapping.get(Result.Status.CRITICAL), (Integer) 503);
        assertEquals(statusMapping.get(Result.Status.HEALTH_CHECK_ERROR), (Integer) 500);

    }

    @Test(expected = ServletException.class)
    public void testGetStatusMappingInvalidToken() throws ServletException {
        healthCheckExecutorServlet.getStatusMapping("CRITICAL");
    }

    @Test(expected = ServletException.class)
    public void testGetStatusMappingInvalidStatus() throws ServletException {
        healthCheckExecutorServlet.getStatusMapping("INVALID:200");
    }

    @Test(expected = ServletException.class)
    public void testGetStatusMappingInvalidStatusCode() throws ServletException {
        healthCheckExecutorServlet.getStatusMapping("CRITICAL:xxx");
    }

    static Result resultEquals(Result expected) {
        return argThat(new ResultMatcher(expected));
    }

    static class ResultMatcher extends ArgumentMatcher<Result> {

        private final Result expectedResult;

        public ResultMatcher(Result expected) {
            this.expectedResult = expected;
        }

        @Override
        public boolean matches(Object actual) {
            Result actualResult = (Result) actual;
            return actualResult.getStatus().equals(expectedResult.getStatus()); // simple status matching only sufficient for this test case
        }

        @Override
        public void describeTo(Description description) {
            description.appendText(expectedResult == null ? null : expectedResult.toString());
        }
    }


}
