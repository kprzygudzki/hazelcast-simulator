package com.hazelcast.simulator.coordinator;

import com.hazelcast.simulator.agent.workerprocess.WorkerProcessSettings;
import com.hazelcast.simulator.common.TestCase;
import com.hazelcast.simulator.common.TestSuite;
import com.hazelcast.simulator.protocol.connector.CoordinatorConnector;
import com.hazelcast.simulator.protocol.core.AddressLevel;
import com.hazelcast.simulator.protocol.core.Response;
import com.hazelcast.simulator.protocol.core.ResponseType;
import com.hazelcast.simulator.protocol.core.SimulatorAddress;
import com.hazelcast.simulator.protocol.core.SimulatorProtocolException;
import com.hazelcast.simulator.protocol.operation.IntegrationTestOperation;
import com.hazelcast.simulator.protocol.operation.LogOperation;
import com.hazelcast.simulator.protocol.operation.PingOperation;
import com.hazelcast.simulator.protocol.operation.SimulatorOperation;
import com.hazelcast.simulator.protocol.registry.ComponentRegistry;
import com.hazelcast.simulator.utils.CommandLineExitException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.hazelcast.simulator.protocol.core.SimulatorAddress.ALL_AGENTS;
import static com.hazelcast.simulator.protocol.core.SimulatorAddress.ALL_WORKERS;
import static com.hazelcast.simulator.protocol.core.SimulatorAddress.COORDINATOR;
import static com.hazelcast.simulator.utils.CommonUtils.closeQuietly;
import static com.hazelcast.simulator.utils.CommonUtils.sleepMillis;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RemoteClientTest {

    private static final int WORKER_PING_INTERVAL_MILLIS = (int) TimeUnit.SECONDS.toMillis(10);
    private static final IntegrationTestOperation DEFAULT_OPERATION = new IntegrationTestOperation();
    private static final String DEFAULT_TEST_ID = "RemoteClientTest";

    private final ComponentRegistry componentRegistry = new ComponentRegistry();

    private final CoordinatorConnector coordinatorConnector = mock(CoordinatorConnector.class);
    private RemoteClient remoteClient;

    @Before
    public void setUp() {
        componentRegistry.addAgent("192.168.0.1", "192.168.0.1");
        componentRegistry.addAgent("192.168.0.2", "192.168.0.2");
        componentRegistry.addAgent("192.168.0.3", "192.168.0.3");

        WorkerProcessSettings workerProcessSettings = mock(WorkerProcessSettings.class);
        when(workerProcessSettings.getWorkerIndex()).thenReturn(1);

        SimulatorAddress agentAddress = componentRegistry.getFirstAgent().getAddress();
        componentRegistry.addWorkers(agentAddress, Collections.singletonList(workerProcessSettings));

        TestCase testCase = new TestCase(DEFAULT_TEST_ID);

        TestSuite testSuite = new TestSuite("RemoteClientTest");
        testSuite.addTest(testCase);

        componentRegistry.addTests(testSuite);
    }

    @After
    public void after() {
        closeQuietly(remoteClient);
    }

    @Test
    public void testLogOnAllAgents() {
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        remoteClient.logOnAllAgents("test");

        verify(coordinatorConnector).write(eq(ALL_AGENTS), any(LogOperation.class));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testLogOnAllWorkers() {
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        remoteClient.logOnAllWorkers("test");

        verify(coordinatorConnector).write(eq(ALL_WORKERS), any(LogOperation.class));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testSendToAllAgents() {
        initMock(ResponseType.SUCCESS);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        remoteClient.sendToAllAgents(DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(ALL_AGENTS), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testSendToAllAgents_withFailureResponse() {
        initMock(ResponseType.UNBLOCKED_BY_FAILURE);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        remoteClient.sendToAllAgents(DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(ALL_AGENTS), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test(expected = CommandLineExitException.class)
    public void testSendToAllAgents_withErrorResponse() {
        initMock(ResponseType.EXCEPTION_DURING_OPERATION_EXECUTION);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        try {
            remoteClient.sendToAllAgents(DEFAULT_OPERATION);
        } finally {
            verify(coordinatorConnector).write(eq(ALL_AGENTS), eq(DEFAULT_OPERATION));
            verifyNoMoreInteractions(coordinatorConnector);
        }
    }

    @Test
    public void testSendToAllWorkers() {
        initMock(ResponseType.SUCCESS);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        remoteClient.sendToAllWorkers(DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(ALL_WORKERS), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testSendToAllWorkers_withFailureResponse() {
        initMock(ResponseType.UNBLOCKED_BY_FAILURE);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        remoteClient.sendToAllWorkers(DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(ALL_WORKERS), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test(expected = CommandLineExitException.class)
    public void testSendToAllWorkers_withErrorResponse() {
        initMock(ResponseType.EXCEPTION_DURING_OPERATION_EXECUTION);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        try {
            remoteClient.sendToAllWorkers(DEFAULT_OPERATION);
        } finally {
            verify(coordinatorConnector).write(eq(ALL_WORKERS), eq(DEFAULT_OPERATION));
            verifyNoMoreInteractions(coordinatorConnector);
        }
    }

    @Test
    public void testSendToFirstWorker() {
        initMock(ResponseType.SUCCESS);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        SimulatorAddress firstWorkerAddress = componentRegistry.getFirstWorker().getAddress();

        remoteClient.sendToFirstWorker(DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(firstWorkerAddress), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testSendToFirstWorker_withFailureResponse() {
        initMock(ResponseType.UNBLOCKED_BY_FAILURE);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        SimulatorAddress firstWorkerAddress = componentRegistry.getFirstWorker().getAddress();

        remoteClient.sendToFirstWorker(DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(firstWorkerAddress), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test(expected = CommandLineExitException.class)
    public void testSendToFirstWorker_withErrorResponse() {
        initMock(ResponseType.EXCEPTION_DURING_OPERATION_EXECUTION);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        SimulatorAddress firstWorkerAddress = componentRegistry.getFirstWorker().getAddress();

        try {
            remoteClient.sendToFirstWorker(DEFAULT_OPERATION);
        } finally {
            verify(coordinatorConnector).write(eq(firstWorkerAddress), eq(DEFAULT_OPERATION));
            verifyNoMoreInteractions(coordinatorConnector);
        }
    }

    @Test
    public void testSendToTestOnAllWorkers() {
        initMock(ResponseType.SUCCESS);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        remoteClient.sendToTestOnAllWorkers(DEFAULT_TEST_ID, DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(ALL_WORKERS.getChild(1)), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testSendToTestOnAllWorkers_withFailureResponse() {
        initMock(ResponseType.UNBLOCKED_BY_FAILURE);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        remoteClient.sendToTestOnAllWorkers(DEFAULT_TEST_ID, DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(ALL_WORKERS.getChild(1)), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test(expected = CommandLineExitException.class)
    public void testSendToTestOnAllWorkers_withErrorResponse() {
        initMock(ResponseType.EXCEPTION_DURING_OPERATION_EXECUTION);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);

        try {
            remoteClient.sendToTestOnAllWorkers(DEFAULT_TEST_ID, DEFAULT_OPERATION);
        } finally {
            verify(coordinatorConnector).write(eq(ALL_WORKERS.getChild(1)), eq(DEFAULT_OPERATION));
            verifyNoMoreInteractions(coordinatorConnector);
        }
    }

    @Test
    public void testSendToTestOnFirstWorker() {
        initMock(ResponseType.SUCCESS);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        SimulatorAddress testOnFirstWorkerAddress = componentRegistry.getFirstWorker().getAddress().getChild(1);

        remoteClient.sendToTestOnFirstWorker(DEFAULT_TEST_ID, DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(testOnFirstWorkerAddress), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testSendToTestOnFirstWorker_withFailureResponse() {
        initMock(ResponseType.UNBLOCKED_BY_FAILURE);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        SimulatorAddress testOnFirstWorkerAddress = componentRegistry.getFirstWorker().getAddress().getChild(1);

        remoteClient.sendToTestOnFirstWorker(DEFAULT_TEST_ID, DEFAULT_OPERATION);

        verify(coordinatorConnector).write(eq(testOnFirstWorkerAddress), eq(DEFAULT_OPERATION));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test(expected = CommandLineExitException.class)
    public void testSendToTestOnFirstWorker_withErrorResponse() {
        initMock(ResponseType.EXCEPTION_DURING_OPERATION_EXECUTION);
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 0);
        SimulatorAddress testOnFirstWorkerAddress = componentRegistry.getFirstWorker().getAddress().getChild(1);

        try {
            remoteClient.sendToTestOnFirstWorker(DEFAULT_TEST_ID, DEFAULT_OPERATION);
        } finally {
            verify(coordinatorConnector).write(eq(testOnFirstWorkerAddress), eq(DEFAULT_OPERATION));
            verifyNoMoreInteractions(coordinatorConnector);
        }
    }

    @Test
    public void testPingWorkerThread_shouldStopAfterInterruptedException() {
        Response response = new Response(1L, ALL_WORKERS);
        response.addResponse(new SimulatorAddress(AddressLevel.WORKER, 1, 1, 0), ResponseType.SUCCESS);

        when(coordinatorConnector.write(eq(ALL_WORKERS), any(PingOperation.class)))
                .thenThrow(new SimulatorProtocolException("expected exception", new InterruptedException()))
                .thenReturn(response);

        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 50);

        sleepMillis(300);

        remoteClient.close();

        verify(coordinatorConnector).write(eq(ALL_WORKERS), any(PingOperation.class));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testPingWorkerThread_shouldContinueAfterOtherException() {
        Response response = new Response(1L, ALL_WORKERS);
        response.addResponse(new SimulatorAddress(AddressLevel.WORKER, 1, 1, 0), ResponseType.SUCCESS);

        when(coordinatorConnector.write(eq(ALL_WORKERS), any(PingOperation.class)))
                .thenThrow(new SimulatorProtocolException("expected exception", new TimeoutException()))
                .thenReturn(response);

        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, 50);

        sleepMillis(300);

        remoteClient.close();

        verify(coordinatorConnector, atLeast(2)).write(eq(ALL_WORKERS), any(PingOperation.class));
        verifyNoMoreInteractions(coordinatorConnector);
    }

    @Test
    public void testPingWorkerThread_shouldDoNothingIfDisabled() {
        remoteClient = new RemoteClient(coordinatorConnector, componentRegistry, -1);

        sleepMillis(300);

        remoteClient.close();

        verifyNoMoreInteractions(coordinatorConnector);
    }

    private void initMock(ResponseType responseType) {
        Map<SimulatorAddress, ResponseType> responseTypes = new HashMap<SimulatorAddress, ResponseType>();
        responseTypes.put(COORDINATOR, responseType);

        Response response = mock(Response.class);
        when(response.entrySet()).thenReturn(responseTypes.entrySet());

        when(coordinatorConnector.write(any(SimulatorAddress.class), any(SimulatorOperation.class))).thenReturn(response);
    }
}
