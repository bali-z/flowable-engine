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
package org.flowable.engine.impl.util;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.junit.Test;

/**
 * Unit tests for {@link WorkflowStepBackUtils}.
 * 
 * @author Flowable
 */
public class WorkflowStepBackUtilsTest {

    @Test
    public void testValidateProcessInstanceId_NullValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateProcessInstanceId(null)
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Process instance ID cannot be null or empty") &&
            exception.getMessage().contains("流程实例ID不能为空"));
    }

    @Test
    public void testValidateProcessInstanceId_EmptyValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateProcessInstanceId("")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Process instance ID cannot be null or empty") &&
            exception.getMessage().contains("流程实例ID不能为空"));
    }

    @Test
    public void testValidateProcessInstanceId_WhitespaceValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateProcessInstanceId("   ")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Process instance ID cannot be null or empty") &&
            exception.getMessage().contains("流程实例ID不能为空"));
    }

    @Test
    public void testValidateProcessInstanceId_ValidValue() {
        // Should not throw any exception
        WorkflowStepBackUtils.validateProcessInstanceId("valid-process-id");
    }

    @Test
    public void testValidateCurrentTaskKey_NullValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateCurrentTaskKey(null)
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Current task key cannot be null or empty") &&
            exception.getMessage().contains("当前任务键不能为空"));
    }

    @Test
    public void testValidateCurrentTaskKey_EmptyValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateCurrentTaskKey("")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Current task key cannot be null or empty") &&
            exception.getMessage().contains("当前任务键不能为空"));
    }

    @Test
    public void testValidateCurrentTaskKey_ValidValue() {
        // Should not throw any exception
        WorkflowStepBackUtils.validateCurrentTaskKey("valid-task-key");
    }

    @Test
    public void testValidateTargetTaskKey_NullValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateTargetTaskKey(null)
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Target task key cannot be null or empty") &&
            exception.getMessage().contains("目标任务键不能为空"));
    }

    @Test
    public void testValidateTargetTaskKey_EmptyValue() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateTargetTaskKey("")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Target task key cannot be null or empty") &&
            exception.getMessage().contains("目标任务键不能为空"));
    }

    @Test
    public void testValidateTargetTaskKey_ValidValue() {
        // Should not throw any exception
        WorkflowStepBackUtils.validateTargetTaskKey("valid-target-key");
    }

    @Test
    public void testValidateStepBackParameters_AllValid() {
        // Should not throw any exception
        WorkflowStepBackUtils.validateStepBackParameters("process-id", "current-task", "target-task");
    }

    @Test
    public void testValidateStepBackParameters_InvalidProcessInstanceId() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateStepBackParameters(null, "current-task", "target-task")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Process instance ID cannot be null or empty") &&
            exception.getMessage().contains("流程实例ID不能为空"));
    }

    @Test
    public void testValidateStepBackParameters_InvalidCurrentTaskKey() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateStepBackParameters("process-id", "", "target-task")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Current task key cannot be null or empty") &&
            exception.getMessage().contains("当前任务键不能为空"));
    }

    @Test
    public void testValidateStepBackParameters_InvalidTargetTaskKey() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateStepBackParameters("process-id", "current-task", null)
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Target task key cannot be null or empty") &&
            exception.getMessage().contains("目标任务键不能为空"));
    }

    @Test
    public void testValidateTaskKeyExistsInProcessDefinition_NullBpmnModel() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateTaskKeyExistsInProcessDefinition("task-key", null, "Test task")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("BPMN model cannot be null") &&
            exception.getMessage().contains("BPMN模型不能为空"));
    }

    @Test
    public void testValidateTaskKeyExistsInProcessDefinition_TaskKeyNotFound() {
        // Create a mock BPMN model with a process that doesn't contain the task key
        BpmnModel bpmnModel = mock(BpmnModel.class);
        Process process = mock(Process.class);
        when(bpmnModel.getProcesses()).thenReturn(Collections.singletonList(process));
        when(process.getFlowElement("non-existing-task")).thenReturn(null);

        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateTaskKeyExistsInProcessDefinition("non-existing-task", bpmnModel, "Test task")
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Test task 'non-existing-task' does not exist in the process definition") &&
            exception.getMessage().contains("Test task 'non-existing-task' 在流程定义中不存在"));
    }

    @Test
    public void testValidateTaskKeyExistsInProcessDefinition_TaskKeyFound() {
        // Create a mock BPMN model with a process that contains the task key
        BpmnModel bpmnModel = mock(BpmnModel.class);
        Process process = mock(Process.class);
        UserTask userTask = new UserTask();
        userTask.setId("existing-task");
        
        when(bpmnModel.getProcesses()).thenReturn(Collections.singletonList(process));
        when(process.getFlowElement("existing-task")).thenReturn(userTask);

        // Should not throw any exception
        WorkflowStepBackUtils.validateTaskKeyExistsInProcessDefinition("existing-task", bpmnModel, "Test task");
    }

    @Test
    public void testValidateTaskKeysExistInProcessDefinition_BothKeysExist() {
        // Create a mock BPMN model with a process that contains both task keys
        BpmnModel bpmnModel = mock(BpmnModel.class);
        Process process = mock(Process.class);
        UserTask currentTask = new UserTask();
        currentTask.setId("current-task");
        UserTask targetTask = new UserTask();
        targetTask.setId("target-task");
        
        when(bpmnModel.getProcesses()).thenReturn(Collections.singletonList(process));
        when(process.getFlowElement("current-task")).thenReturn(currentTask);
        when(process.getFlowElement("target-task")).thenReturn(targetTask);

        // Should not throw any exception
        WorkflowStepBackUtils.validateTaskKeysExistInProcessDefinition("current-task", "target-task", bpmnModel);
    }

    @Test
    public void testValidateTaskKeysExistInProcessDefinition_CurrentTaskNotFound() {
        // Create a mock BPMN model with a process that doesn't contain current task
        BpmnModel bpmnModel = mock(BpmnModel.class);
        Process process = mock(Process.class);
        UserTask targetTask = new UserTask();
        targetTask.setId("target-task");
        
        when(bpmnModel.getProcesses()).thenReturn(Collections.singletonList(process));
        when(process.getFlowElement("non-existing-current")).thenReturn(null);
        when(process.getFlowElement("target-task")).thenReturn(targetTask);

        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> WorkflowStepBackUtils.validateTaskKeysExistInProcessDefinition("non-existing-current", "target-task", bpmnModel)
        );
        assertTrue("Error message should contain both English and Chinese", 
            exception.getMessage().contains("Current task key 'non-existing-current' does not exist in the process definition") &&
            exception.getMessage().contains("Current task key 'non-existing-current' 在流程定义中不存在"));
    }
}