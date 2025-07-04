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
package org.flowable.engine.impl;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.WorkflowStepBackService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link WorkflowStepBackServiceImpl}.
 * 
 * @author Flowable
 */
public class WorkflowStepBackServiceImplTest {

    private WorkflowStepBackService workflowStepBackService;
    private ProcessEngineConfigurationImpl processEngineConfiguration;
    private RuntimeService runtimeService;
    private RepositoryService repositoryService;

    @Before
    public void setUp() {
        processEngineConfiguration = mock(ProcessEngineConfigurationImpl.class);
        runtimeService = mock(RuntimeService.class);
        repositoryService = mock(RepositoryService.class);
        
        when(processEngineConfiguration.getRuntimeService()).thenReturn(runtimeService);
        when(processEngineConfiguration.getRepositoryService()).thenReturn(repositoryService);
        
        workflowStepBackService = new WorkflowStepBackServiceImpl(processEngineConfiguration);
    }

    @Test
    public void testStepBackWorkflow_ValidParameters() {
        // This test validates that the service correctly validates parameters before attempting step back
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflow("process-1", "current-task", "current-task")
        );
        
        // Should fail with same task error (from our placeholder implementation)
        assertTrue("Error message should contain bilingual message about same task", 
            exception.getMessage().contains("Cannot step back to the same task") &&
            exception.getMessage().contains("不能回退到同一个任务"));
    }

    @Test
    public void testStepBackWorkflow_InvalidProcessInstanceId() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflow(null, "current-task", "target-task")
        );
        
        assertTrue("Error message should contain bilingual message about process instance ID", 
            exception.getMessage().contains("Process instance ID cannot be null or empty") &&
            exception.getMessage().contains("流程实例ID不能为空"));
    }

    @Test
    public void testStepBackWorkflow_InvalidCurrentTaskKey() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflow("process-1", "", "target-task")
        );
        
        assertTrue("Error message should contain bilingual message about current task key", 
            exception.getMessage().contains("Current task key cannot be null or empty") &&
            exception.getMessage().contains("当前任务键不能为空"));
    }

    @Test
    public void testStepBackWorkflow_InvalidTargetTaskKey() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflow("process-1", "current-task", "   ")
        );
        
        assertTrue("Error message should contain bilingual message about target task key", 
            exception.getMessage().contains("Target task key cannot be null or empty") &&
            exception.getMessage().contains("目标任务键不能为空"));
    }

    @Test
    public void testValidateStepBackParameters_AllValid() {
        // Should not throw any exception
        workflowStepBackService.validateStepBackParameters("process-1", "current-task", "target-task");
    }

    @Test
    public void testValidateStepBackParameters_InvalidParameters() {
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.validateStepBackParameters("", "current-task", "target-task")
        );
        
        assertTrue("Error message should contain bilingual message", 
            exception.getMessage().contains("Process instance ID cannot be null or empty") &&
            exception.getMessage().contains("流程实例ID不能为空"));
    }

    @Test
    public void testStepBackWorkflowWithValidation_ProcessInstanceNotFound() {
        // Mock the runtime service to return null (process instance not found)
        ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(anyString())).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(null);
        
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflowWithValidation("process-1", "current-task", "target-task", true)
        );
        
        assertTrue("Error message should contain bilingual message about process instance not found", 
            exception.getMessage().contains("Process instance not found") &&
            exception.getMessage().contains("未找到流程实例ID"));
    }

    @Test
    public void testStepBackWorkflowWithValidation_ProcessDefinitionNotFound() {
        // Mock the runtime service to return a process instance
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("proc-def-1");
        
        ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(anyString())).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        
        // Mock the repository service to return null (process definition not found)
        ProcessDefinitionQuery processDefinitionQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId(anyString())).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(null);
        
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflowWithValidation("process-1", "current-task", "target-task", true)
        );
        
        assertTrue("Error message should contain bilingual message about process definition not found", 
            exception.getMessage().contains("Process definition not found") &&
            exception.getMessage().contains("未找到流程定义ID"));
    }

    @Test
    public void testStepBackWorkflowWithValidation_TaskKeyNotFound() {
        // Mock the runtime service to return a process instance
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("proc-def-1");
        
        ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId(anyString())).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        
        // Mock the repository service to return a process definition
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(processDefinition.getId()).thenReturn("proc-def-1");
        
        ProcessDefinitionQuery processDefinitionQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId(anyString())).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(processDefinition);
        
        // Mock the BPMN model without the required task keys
        BpmnModel bpmnModel = mock(BpmnModel.class);
        Process process = mock(Process.class);
        when(bpmnModel.getProcesses()).thenReturn(Collections.singletonList(process));
        when(process.getFlowElement("current-task")).thenReturn(null); // Task not found
        when(repositoryService.getBpmnModel(anyString())).thenReturn(bpmnModel);
        
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflowWithValidation("process-1", "current-task", "target-task", true)
        );
        
        assertTrue("Error message should contain bilingual message about task key not found", 
            exception.getMessage().contains("Current task key 'current-task' does not exist in the process definition") &&
            exception.getMessage().contains("Current task key 'current-task' 在流程定义中不存在"));
    }

    @Test
    public void testStepBackWorkflowWithValidation_WithoutTaskKeyValidation() {
        // Should only validate basic parameters and skip task key validation
        FlowableIllegalArgumentException exception = assertThrows(
            FlowableIllegalArgumentException.class,
            () -> workflowStepBackService.stepBackWorkflowWithValidation("process-1", "current-task", "current-task", false)
        );
        
        // Should fail with same task error (from our placeholder implementation)
        assertTrue("Error message should contain bilingual message about same task", 
            exception.getMessage().contains("Cannot step back to the same task") &&
            exception.getMessage().contains("不能回退到同一个任务"));
    }
}