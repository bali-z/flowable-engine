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

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;

/**
 * Utility class for workflow step back validation.
 * Provides comprehensive parameter validation with bilingual error messages.
 * 
 * @author Flowable
 */
public class WorkflowStepBackUtils {

    /**
     * Validates all required parameters for workflow step back operation.
     * 
     * @param processInstanceId the process instance ID
     * @param currentTaskKey the current task key
     * @param targetTaskKey the target task key to step back to
     * @throws FlowableIllegalArgumentException if any validation fails
     */
    public static void validateStepBackParameters(String processInstanceId, String currentTaskKey, String targetTaskKey) {
        validateProcessInstanceId(processInstanceId);
        validateCurrentTaskKey(currentTaskKey);
        validateTargetTaskKey(targetTaskKey);
    }

    /**
     * Validates all required parameters and task key existence in process definition.
     * 
     * @param processInstanceId the process instance ID
     * @param currentTaskKey the current task key
     * @param targetTaskKey the target task key to step back to
     * @param repositoryService the repository service to get BPMN model
     * @param processDefinition the process definition to validate task keys against
     * @throws FlowableIllegalArgumentException if any validation fails
     */
    public static void validateStepBackParametersWithProcessDefinition(String processInstanceId, 
                                                                       String currentTaskKey, 
                                                                       String targetTaskKey, 
                                                                       RepositoryService repositoryService,
                                                                       ProcessDefinition processDefinition) {
        validateStepBackParameters(processInstanceId, currentTaskKey, targetTaskKey);
        
        if (processDefinition == null) {
            throw new FlowableIllegalArgumentException(
                "Process definition cannot be null / 流程定义不能为空"
            );
        }
        
        if (repositoryService == null) {
            throw new FlowableIllegalArgumentException(
                "Repository service cannot be null / 仓库服务不能为空"
            );
        }
        
        // Get the BPMN model to validate task keys
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
        
        validateTaskKeyExistsInProcessDefinition(currentTaskKey, bpmnModel, "Current task key");
        validateTaskKeyExistsInProcessDefinition(targetTaskKey, bpmnModel, "Target task key");
    }

    /**
     * Validates that the process instance ID is not null or empty.
     * 
     * @param processInstanceId the process instance ID to validate
     * @throws FlowableIllegalArgumentException if validation fails
     */
    public static void validateProcessInstanceId(String processInstanceId) {
        if (StringUtils.isBlank(processInstanceId)) {
            throw new FlowableIllegalArgumentException(
                "Process instance ID cannot be null or empty / 流程实例ID不能为空"
            );
        }
    }

    /**
     * Validates that the current task key is not null or empty.
     * 
     * @param currentTaskKey the current task key to validate
     * @throws FlowableIllegalArgumentException if validation fails
     */
    public static void validateCurrentTaskKey(String currentTaskKey) {
        if (StringUtils.isBlank(currentTaskKey)) {
            throw new FlowableIllegalArgumentException(
                "Current task key cannot be null or empty / 当前任务键不能为空"
            );
        }
    }

    /**
     * Validates that the target task key is not null or empty.
     * 
     * @param targetTaskKey the target task key to validate
     * @throws FlowableIllegalArgumentException if validation fails
     */
    public static void validateTargetTaskKey(String targetTaskKey) {
        if (StringUtils.isBlank(targetTaskKey)) {
            throw new FlowableIllegalArgumentException(
                "Target task key cannot be null or empty / 目标任务键不能为空"
            );
        }
    }

    /**
     * Validates that a task key exists in the process definition.
     * 
     * @param taskKey the task key to validate
     * @param bpmnModel the BPMN model containing the process definition
     * @param taskDescription description of the task for error messages
     * @throws FlowableIllegalArgumentException if the task key does not exist
     */
    public static void validateTaskKeyExistsInProcessDefinition(String taskKey, BpmnModel bpmnModel, String taskDescription) {
        if (bpmnModel == null) {
            throw new FlowableIllegalArgumentException(
                "BPMN model cannot be null / BPMN模型不能为空"
            );
        }

        boolean taskKeyExists = false;
        for (Process process : bpmnModel.getProcesses()) {
            FlowElement flowElement = process.getFlowElement(taskKey);
            if (flowElement != null) {
                taskKeyExists = true;
                break;
            }
        }

        if (!taskKeyExists) {
            throw new FlowableIllegalArgumentException(
                String.format("%s '%s' does not exist in the process definition / %s '%s' 在流程定义中不存在", 
                    taskDescription, taskKey, taskDescription, taskKey)
            );
        }
    }

    /**
     * Validates task keys existence in process definition without requiring task description.
     * 
     * @param currentTaskKey the current task key
     * @param targetTaskKey the target task key
     * @param bpmnModel the BPMN model containing the process definition
     * @throws FlowableIllegalArgumentException if any task key does not exist
     */
    public static void validateTaskKeysExistInProcessDefinition(String currentTaskKey, String targetTaskKey, BpmnModel bpmnModel) {
        validateTaskKeyExistsInProcessDefinition(currentTaskKey, bpmnModel, "Current task key");
        validateTaskKeyExistsInProcessDefinition(targetTaskKey, bpmnModel, "Target task key");
    }
}