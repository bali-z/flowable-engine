/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.engine.impl.WorkflowStepBackService;
import org.flowable.engine.impl.cmd.WorkflowStepBackException;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.impl.util.WorkflowStepBackUtils;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

/**
 * Test class for workflow step back functionality.
 * 工作流回退功能的测试类。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackTest extends PluggableFlowableTestCase {

    /**
     * Test basic step back functionality.
     * 测试基本回退功能。
     */
    @Test
    @Deployment(resources = "org/flowable/engine/test/api/task/WorkflowStepBackTest.testStepBack.bpmn20.xml")
    public void testBasicStepBack() {
        // Create a step back service instance
        WorkflowStepBackService stepBackService = new WorkflowStepBackService(processEngineConfiguration);
        
        // Start a process instance
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("stepBackProcess");
        
        // Complete the first task
        Task firstTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(firstTask).isNotNull();
        assertThat(firstTask.getTaskDefinitionKey()).isEqualTo("task1");
        
        taskService.complete(firstTask.getId());
        
        // Get the second task
        Task secondTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(secondTask).isNotNull();
        assertThat(secondTask.getTaskDefinitionKey()).isEqualTo("task2");
        
        // Test step back validation
        WorkflowStepBackService.StepBackValidationResult validationResult = 
            stepBackService.validateStepBack(secondTask.getId(), null, "testUser");
        assertThat(validationResult.isValid()).isTrue();
        
        // Get possible step back targets
        List<String> possibleTargets = stepBackService.getPossibleStepBackTargets(secondTask.getId());
        assertThat(possibleTargets).isNotEmpty();
        
        // Test basic step back
        stepBackService.stepBackTask(secondTask.getId(), "testUser", "Testing step back functionality");
        
        // Verify that we're back at the first task
        Task backedTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(backedTask).isNotNull();
        assertThat(backedTask.getTaskDefinitionKey()).isEqualTo("task1");
    }

    /**
     * Test step back to specific activity.
     * 测试回退到指定活动。
     */
    @Test
    @Deployment(resources = "org/flowable/engine/test/api/task/WorkflowStepBackTest.testStepBackToActivity.bpmn20.xml")
    public void testStepBackToActivity() {
        WorkflowStepBackService stepBackService = new WorkflowStepBackService(processEngineConfiguration);
        
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("stepBackToActivityProcess");
        
        // Complete task1
        Task task1 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(task1.getId());
        
        // Complete task2
        Task task2 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(task2.getId());
        
        // Now at task3, step back to task1
        Task task3 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(task3.getTaskDefinitionKey()).isEqualTo("task3");
        
        stepBackService.stepBackToActivity(task3.getId(), "task1", "testUser", "Step back to beginning");
        
        // Verify we're back at task1
        Task backedTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        assertThat(backedTask.getTaskDefinitionKey()).isEqualTo("task1");
    }

    /**
     * Test step back with variables.
     * 测试带变量的回退。
     */
    @Test
    @Deployment(resources = "org/flowable/engine/test/api/task/WorkflowStepBackTest.testStepBackWithVariables.bpmn20.xml")
    public void testStepBackWithVariables() {
        WorkflowStepBackService stepBackService = new WorkflowStepBackService(processEngineConfiguration);
        
        Map<String, Object> startVariables = new HashMap<>();
        startVariables.put("testVar", "initialValue");
        
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("stepBackWithVariablesProcess", startVariables);
        
        // Complete first task
        Task task1 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(task1.getId());
        
        // At second task, step back with new variables
        Task task2 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        
        Map<String, Object> newVariables = new HashMap<>();
        newVariables.put("testVar", "modifiedValue");
        newVariables.put("newVar", "addedValue");
        
        Map<String, Object> localVariables = new HashMap<>();
        localVariables.put("localVar", "localValue");
        
        stepBackService.stepBackWithVariables(task2.getId(), null, "testUser", 
            "Step back with variables", newVariables, localVariables);
        
        // Verify variables were set
        Object testVar = runtimeService.getVariable(processInstance.getId(), "testVar");
        assertThat(testVar).isEqualTo("modifiedValue");
        
        Object newVar = runtimeService.getVariable(processInstance.getId(), "newVar");
        assertThat(newVar).isEqualTo("addedValue");
    }

    /**
     * Test exception handling.
     * 测试异常处理。
     */
    @Test
    public void testStepBackExceptions() {
        WorkflowStepBackService stepBackService = new WorkflowStepBackService(processEngineConfiguration);
        
        // Test with non-existent task
        assertThatThrownBy(() -> {
            stepBackService.stepBackTask("non-existent-task", "testUser", "Test exception");
        }).isInstanceOf(WorkflowStepBackException.class)
          .hasMessageContaining("Task not found");
        
        // Test with empty task ID
        assertThatThrownBy(() -> {
            stepBackService.stepBackTask("", "testUser", "Test exception");
        }).isInstanceOf(WorkflowStepBackException.class)
          .hasMessageContaining("Task ID cannot be empty");
        
        // Test with null task ID
        assertThatThrownBy(() -> {
            stepBackService.stepBackTask(null, "testUser", "Test exception");
        }).isInstanceOf(WorkflowStepBackException.class)
          .hasMessageContaining("Task ID cannot be empty");
    }

    /**
     * Test workflow step back utilities.
     * 测试工作流回退工具类。
     */
    @Test
    @Deployment(resources = "org/flowable/engine/test/api/task/WorkflowStepBackTest.testUtilities.bpmn20.xml")
    public void testWorkflowStepBackUtils() {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("utilitiesProcess");
        Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        
        // Test process type determination
        WorkflowStepBackUtils.ProcessType processType = WorkflowStepBackUtils.determineProcessType(
            (org.flowable.task.service.impl.persistence.entity.TaskEntity) task, null);
        assertThat(processType).isEqualTo(WorkflowStepBackUtils.ProcessType.BPMN);
        
        // Test permission checking
        boolean hasPermission = WorkflowStepBackUtils.hasStepBackPermission(
            (org.flowable.task.service.impl.persistence.entity.TaskEntity) task, "testUser");
        // Should be false since testUser is not the assignee
        assertThat(hasPermission).isFalse();
        
        // Assign task to testUser and test again
        taskService.setAssignee(task.getId(), "testUser");
        task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        hasPermission = WorkflowStepBackUtils.hasStepBackPermission(
            (org.flowable.task.service.impl.persistence.entity.TaskEntity) task, "testUser");
        assertThat(hasPermission).isTrue();
        
        // Test step back message creation
        String stepBackMessage = WorkflowStepBackUtils.createStepBackMessage("testUser", "Test reason");
        assertThat(stepBackMessage).contains("testUser");
        assertThat(stepBackMessage).contains("Test reason");
        assertThat(stepBackMessage).contains("用户回退"); // Chinese text
        
        // Test multi-instance detection
        boolean isMultiInstance = WorkflowStepBackUtils.isMultiInstanceTask(
            (org.flowable.task.service.impl.persistence.entity.TaskEntity) task);
        assertThat(isMultiInstance).isFalse(); // This is not a multi-instance task
        
        // Test subprocess detection
        boolean isSubprocess = WorkflowStepBackUtils.isSubprocessTask(
            (org.flowable.task.service.impl.persistence.entity.TaskEntity) task);
        assertThat(isSubprocess).isFalse(); // This is not a subprocess task
    }

    /**
     * Test error codes and exception details.
     * 测试错误代码和异常详情。
     */
    @Test
    public void testWorkflowStepBackExceptionDetails() {
        // Test different error codes
        WorkflowStepBackException.StepBackErrorCode[] errorCodes = 
            WorkflowStepBackException.StepBackErrorCode.values();
        
        assertThat(errorCodes).isNotEmpty();
        
        for (WorkflowStepBackException.StepBackErrorCode errorCode : errorCodes) {
            assertThat(errorCode.getCode()).isNotEmpty();
            assertThat(errorCode.getDescription()).isNotEmpty();
        }
        
        // Test exception with context
        WorkflowStepBackException exception = new WorkflowStepBackException(
            WorkflowStepBackException.StepBackErrorCode.TASK_NOT_FOUND,
            "Test message",
            "task123",
            "process456",
            "activity789"
        );
        
        assertThat(exception.getErrorCode()).isEqualTo(WorkflowStepBackException.StepBackErrorCode.TASK_NOT_FOUND);
        assertThat(exception.getTaskId()).isEqualTo("task123");
        assertThat(exception.getProcessInstanceId()).isEqualTo("process456");
        assertThat(exception.getTargetActivityId()).isEqualTo("activity789");
        assertThat(exception.getMessage()).isEqualTo("Test message");
        
        // Test toString method
        String exceptionString = exception.toString();
        assertThat(exceptionString).contains("task123");
        assertThat(exceptionString).contains("process456");
        assertThat(exceptionString).contains("activity789");
    }

    /**
     * Test that step back is supported.
     * 测试是否支持回退。
     */
    @Test
    public void testStepBackSupported() {
        WorkflowStepBackService stepBackService = new WorkflowStepBackService(processEngineConfiguration);
        assertThat(stepBackService.isStepBackSupported()).isTrue();
    }
}