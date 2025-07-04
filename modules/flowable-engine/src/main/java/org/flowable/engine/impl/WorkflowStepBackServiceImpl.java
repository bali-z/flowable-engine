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

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.service.CommonServiceImpl;
import org.flowable.engine.WorkflowStepBackService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.WorkflowStepBackUtils;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;

/**
 * Implementation of {@link WorkflowStepBackService} with comprehensive validation.
 * All step back operations include thorough parameter validation before execution.
 * 
 * @author Flowable
 */
public class WorkflowStepBackServiceImpl extends CommonServiceImpl<ProcessEngineConfigurationImpl> implements WorkflowStepBackService {

    public WorkflowStepBackServiceImpl(ProcessEngineConfigurationImpl processEngineConfiguration) {
        super(processEngineConfiguration);
    }

    @Override
    public void stepBackWorkflow(String processInstanceId, String currentTaskKey, String targetTaskKey) {
        // Always validate parameters before performing step back
        WorkflowStepBackUtils.validateStepBackParameters(processInstanceId, currentTaskKey, targetTaskKey);
        
        // Perform the actual step back operation
        performStepBack(processInstanceId, currentTaskKey, targetTaskKey);
    }

    @Override
    public void validateStepBackParameters(String processInstanceId, String currentTaskKey, String targetTaskKey) {
        WorkflowStepBackUtils.validateStepBackParameters(processInstanceId, currentTaskKey, targetTaskKey);
    }

    @Override
    public void stepBackWorkflowWithValidation(String processInstanceId, String currentTaskKey, String targetTaskKey, boolean validateTaskKeys) {
        // Always validate basic parameters
        WorkflowStepBackUtils.validateStepBackParameters(processInstanceId, currentTaskKey, targetTaskKey);
        
        if (validateTaskKeys) {
            // Get process instance and definition for task key validation
            ProcessInstance processInstance = getProcessInstance(processInstanceId);
            ProcessDefinition processDefinition = getProcessDefinition(processInstance.getProcessDefinitionId());
            
            // Validate task keys exist in process definition
            WorkflowStepBackUtils.validateStepBackParametersWithProcessDefinition(
                processInstanceId, currentTaskKey, targetTaskKey, configuration.getRepositoryService(), processDefinition);
        }
        
        // Perform the actual step back operation
        performStepBack(processInstanceId, currentTaskKey, targetTaskKey);
    }

    /**
     * Performs the actual step back operation.
     * Note: This is a placeholder implementation. In a real implementation,
     * this would contain the actual logic to step back the workflow.
     * 
     * @param processInstanceId the process instance ID
     * @param currentTaskKey the current task key
     * @param targetTaskKey the target task key
     */
    private void performStepBack(String processInstanceId, String currentTaskKey, String targetTaskKey) {
        // TODO: Implement actual step back logic
        // This would typically involve:
        // 1. Finding the current active task(s)
        // 2. Creating new task instances at the target task
        // 3. Completing/canceling current task(s)
        // 4. Managing process variables and history
        
        // For now, just log the operation (in a real implementation, use proper logging)
        // System.out.println("Stepping back process " + processInstanceId + " from " + currentTaskKey + " to " + targetTaskKey);
        
        // Placeholder: throw exception if trying to step back to same task
        if (currentTaskKey.equals(targetTaskKey)) {
            throw new FlowableIllegalArgumentException(
                "Cannot step back to the same task / 不能回退到同一个任务: " + currentTaskKey
            );
        }
    }

    /**
     * Gets the process instance by ID with validation.
     * 
     * @param processInstanceId the process instance ID
     * @return the process instance
     * @throws FlowableIllegalArgumentException if process instance not found
     */
    private ProcessInstance getProcessInstance(String processInstanceId) {
        ProcessInstance processInstance = configuration.getRuntimeService()
            .createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
            
        if (processInstance == null) {
            throw new FlowableIllegalArgumentException(
                "Process instance not found with ID: " + processInstanceId + 
                " / 未找到流程实例ID: " + processInstanceId
            );
        }
        
        return processInstance;
    }

    /**
     * Gets the process definition by ID.
     * 
     * @param processDefinitionId the process definition ID
     * @return the process definition
     * @throws FlowableIllegalArgumentException if process definition not found
     */
    private ProcessDefinition getProcessDefinition(String processDefinitionId) {
        ProcessDefinition processDefinition = configuration.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionId(processDefinitionId)
            .singleResult();
            
        if (processDefinition == null) {
            throw new FlowableIllegalArgumentException(
                "Process definition not found with ID: " + processDefinitionId + 
                " / 未找到流程定义ID: " + processDefinitionId
            );
        }
        
        return processDefinition;
    }
}