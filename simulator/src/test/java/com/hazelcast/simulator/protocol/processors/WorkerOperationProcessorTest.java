package com.hazelcast.simulator.protocol.processors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.simulator.protocol.core.ResponseType;
import com.hazelcast.simulator.protocol.operation.CreateTestOperation;
import com.hazelcast.simulator.protocol.operation.IntegrationTestOperation;
import com.hazelcast.simulator.protocol.operation.IsPhaseCompletedOperation;
import com.hazelcast.simulator.protocol.operation.SimulatorOperation;
import com.hazelcast.simulator.protocol.operation.StartTestOperation;
import com.hazelcast.simulator.protocol.operation.StartTestPhaseOperation;
import com.hazelcast.simulator.protocol.operation.StopTestOperation;
import com.hazelcast.simulator.test.TestCase;
import com.hazelcast.simulator.test.TestPhase;
import com.hazelcast.simulator.tests.FailingTest;
import com.hazelcast.simulator.tests.SuccessTest;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hazelcast.simulator.protocol.core.ResponseType.EXCEPTION_DURING_OPERATION_EXECUTION;
import static com.hazelcast.simulator.protocol.core.ResponseType.SUCCESS;
import static com.hazelcast.simulator.protocol.core.ResponseType.TEST_PHASE_IS_RUNNING;
import static com.hazelcast.simulator.protocol.core.ResponseType.UNSUPPORTED_OPERATION_ON_THIS_PROCESSOR;
import static com.hazelcast.simulator.protocol.operation.OperationCodec.toJson;
import static com.hazelcast.simulator.protocol.operation.OperationType.getOperationType;
import static com.hazelcast.simulator.utils.CommonUtils.sleepMillis;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WorkerOperationProcessorTest {

    private static final Logger LOGGER = Logger.getLogger(WorkerOperationProcessorTest.class);

    private static final Class DEFAULT_TEST = SuccessTest.class;
    private static final String DEFAULT_TEST_ID = DEFAULT_TEST.getSimpleName();

    private final TestCase defaultTestCase = mock(TestCase.class);

    private final TestExceptionLogger exceptionLogger = new TestExceptionLogger();

    private final HazelcastInstance serverInstance = mock(HazelcastInstance.class);
    private final HazelcastInstance clientInstance = mock(HazelcastInstance.class);

    private Map<String, String> properties;
    private WorkerOperationProcessor processor;

    @Before
    public void setUp() throws Exception {
        properties = new HashMap<String, String>();
        setTestCaseClass(DEFAULT_TEST.getName());

        when(defaultTestCase.getId()).thenReturn(DEFAULT_TEST_ID);
        when(defaultTestCase.getProperties()).thenReturn(properties);

        when(serverInstance.getUserContext()).thenReturn(new ConcurrentHashMap<String, Object>());
        when(clientInstance.getUserContext()).thenReturn(new ConcurrentHashMap<String, Object>());

        processor = new WorkerOperationProcessor(exceptionLogger, serverInstance, clientInstance);
    }

    @Test
    public void process_unsupportedCommand() throws Exception {
        SimulatorOperation operation = new IntegrationTestOperation(IntegrationTestOperation.TEST_DATA);
        ResponseType responseType = processor.processOperation(getOperationType(operation), operation);

        assertEquals(UNSUPPORTED_OPERATION_ON_THIS_PROCESSOR, responseType);
        exceptionLogger.assertNoException();
    }

    @Test
    public void process_CreateTest() throws Exception {
        ResponseType responseType = createTestCase(defaultTestCase);

        assertEquals(SUCCESS, responseType);
        exceptionLogger.assertNoException();
    }

    @Test
    public void process_CreateTest_sameTestIdTwice() throws Exception {
        ResponseType responseType = createTestCase(defaultTestCase);
        assertEquals(SUCCESS, responseType);

        responseType = createTestCase(defaultTestCase);
        assertEquals(EXCEPTION_DURING_OPERATION_EXECUTION, responseType);
        exceptionLogger.assertException(IllegalStateException.class);
    }

    @Test
    public void process_CreateTest_invalidTestId() {
        ResponseType responseType = createTestCase(SuccessTest.class, "%&/?!");

        assertEquals(EXCEPTION_DURING_OPERATION_EXECUTION, responseType);
        exceptionLogger.assertException(IllegalArgumentException.class);
    }

    @Test
    public void process_CreateTest_invalidClassPath() {
        setTestCaseClass("not.found.SuccessTest");
        ResponseType responseType = createTestCase(defaultTestCase);

        assertEquals(EXCEPTION_DURING_OPERATION_EXECUTION, responseType);
        exceptionLogger.assertException(ClassNotFoundException.class);
    }

    @Test
    public void process_StartTest() {
        createTestCase(defaultTestCase);
        runPhase(DEFAULT_TEST_ID, TestPhase.SETUP);
        stopTest(DEFAULT_TEST_ID, 500);
        runTest(DEFAULT_TEST_ID);

        exceptionLogger.assertNoException();
    }

    @Test
    public void process_StartTest_failingTest() {
        Class testClass = FailingTest.class;
        String testId = testClass.getSimpleName();

        createTestCase(testClass, testId);
        runPhase(testId, TestPhase.SETUP);
        runTest(testId);

        exceptionLogger.assertException(RuntimeException.class);
    }

    @Test
    public void process_StartTest_noSetUp() {
        createTestCase(defaultTestCase);
        runTest(DEFAULT_TEST_ID);

        // no setup was executed, so TestContext is null
        exceptionLogger.assertException(NullPointerException.class);
    }

    @Test
    public void process_StartTest_testNotFound() {
        runTest("notFound");

        exceptionLogger.assertNoException();
    }

    @Test
    public void process_StartTest_passiveMember() {
        processor = new WorkerOperationProcessor(exceptionLogger, serverInstance, null);

        createTestCase(defaultTestCase);
        StartTestOperation operation = new StartTestOperation(DEFAULT_TEST_ID, true);
        ResponseType responseType = processor.process(operation);
        assertEquals(SUCCESS, responseType);

        waitForPhaseCompletion(DEFAULT_TEST_ID, TestPhase.RUN);

        exceptionLogger.assertNoException();
    }

    @Test
    public void process_StopTest_testNotFound() {
        stopTest("notFound", 0);
    }

    @Test
    public void process_StartTestPhase_testNotFound() {
        runPhase("notFound", TestPhase.SETUP);
    }

    @Test
    public void process_StartTestPhase_failingTest() {
        String testId = "FailingTest";
        createTestCase(FailingTest.class, testId);
        runPhase(testId, TestPhase.GLOBAL_VERIFY);

        exceptionLogger.assertException(RuntimeException.class);
    }

    @Test
    public void process_StartTestPhase_oldPhaseStillRunning() {
        createTestCase(defaultTestCase);
        runPhase(DEFAULT_TEST_ID, TestPhase.SETUP);

        StartTestPhaseOperation operation = new StartTestPhaseOperation(DEFAULT_TEST_ID, TestPhase.RUN);
        processor.process(operation);

        runPhase(DEFAULT_TEST_ID, TestPhase.LOCAL_VERIFY, EXCEPTION_DURING_OPERATION_EXECUTION);

        exceptionLogger.assertException(IllegalStateException.class, IllegalStateException.class);
    }

    @Test
    public void process_StartTestPhase_removeTestAfterLocalTearDown() {
        createTestCase(defaultTestCase);
        runPhase(DEFAULT_TEST_ID, TestPhase.LOCAL_TEARDOWN);

        // we should be able to init the test again, after it has been removed
        createTestCase(defaultTestCase);

        exceptionLogger.assertNoException();
    }

    private void setTestCaseClass(String className) {
        properties.put("class", className);
    }

    private ResponseType createTestCase(Class testClass, String testId) {
        setTestCaseClass(testClass.getName());
        TestCase testCase = mock(TestCase.class);
        when(testCase.getId()).thenReturn(testId);
        when(testCase.getProperties()).thenReturn(properties);

        return createTestCase(testCase);
    }

    private ResponseType createTestCase(TestCase testCase) {
        SimulatorOperation operation = new CreateTestOperation(testCase);
        LOGGER.debug("Serialized operation: " + toJson(operation));

        return processor.process(operation);
    }

    private void waitForPhaseCompletion(String testId, TestPhase testPhase) {
        IsPhaseCompletedOperation operation = new IsPhaseCompletedOperation(testId, testPhase);
        ResponseType responseType;
        do {
            sleepMillis(100);
            responseType = processor.process(operation);
            if (responseType == null) {
                fail("Got null response on IsPhaseCompletedCommand");
            }
            LOGGER.info("Phase " + testPhase + ": " + responseType);
        } while (TEST_PHASE_IS_RUNNING.equals(responseType));
    }

    private void runPhase(String testId, TestPhase testPhase) {
        runPhase(testId, testPhase, SUCCESS);
    }

    private void runPhase(String testId, TestPhase testPhase, ResponseType expectedResponseType) {
        StartTestPhaseOperation operation = new StartTestPhaseOperation(testId, testPhase);
        ResponseType responseType = processor.process(operation);

        assertEquals(expectedResponseType, responseType);

        waitForPhaseCompletion(testId, testPhase);
    }

    private void runTest(String testId) {
        StartTestOperation operation = new StartTestOperation(testId, false);
        processor.process(operation);

        waitForPhaseCompletion(testId, TestPhase.RUN);
    }

    private void stopTest(final String testId, final int delayMs) {
        Thread stopThread = new Thread() {
            @Override
            public void run() {
                sleepMillis(delayMs);
                StopTestOperation operation = new StopTestOperation(testId);
                processor.process(operation);
            }
        };
        stopThread.start();
    }
}
