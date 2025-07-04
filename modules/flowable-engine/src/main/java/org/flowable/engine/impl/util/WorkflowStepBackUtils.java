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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.cmd.WorkflowStepBackException;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for workflow step back operations.
 * 工作流回退操作的工具类。
 * 
 * This class provides helper methods for task and execution operations,
 * process type determination, and validation methods needed for step back functionality.
 * 此类为回退功能提供任务和执行操作、流程类型确定和验证方法的辅助方法。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackUtils.class);

    private WorkflowStepBackUtils() {
        // Utility class
    }

    /**
     * Process types supported by step back functionality.
     * 回退功能支持的流程类型。
     */
    public enum ProcessType {
        BPMN("BPMN Process", "BPMN流程"),
        CMMN("CMMN Case", "CMMN案例"),
        DMN("DMN Decision", "DMN决策"),
        UNKNOWN("Unknown Process", "未知流程");

        private final String englishDescription;
        private final String chineseDescription;

        ProcessType(String englishDescription, String chineseDescription) {
            this.englishDescription = englishDescription;
            this.chineseDescription = chineseDescription;
        }

        public String getEnglishDescription() {
            return englishDescription;
        }

        public String getChineseDescription() {
            return chineseDescription;
        }
    }

    /**
     * Determines the process type based on task or execution context.
     * 根据任务或执行上下文确定流程类型。
     * 
     * @param task The task entity, may be null
     * @param execution The execution entity, may be null
     * @return The determined process type
     */
    public static ProcessType determineProcessType(TaskEntity task, ExecutionEntity execution) {
        if (task != null) {
            if (StringUtils.isNotEmpty(task.getScopeType())) {
                if (ScopeTypes.CMMN.equals(task.getScopeType())) {
                    return ProcessType.CMMN;
                } else if (ScopeTypes.BPMN.equals(task.getScopeType())) {
                    return ProcessType.BPMN;
                }
            }
            if (StringUtils.isNotEmpty(task.getProcessDefinitionId())) {
                return ProcessType.BPMN;
            }
        }

        if (execution != null) {
            if (StringUtils.isNotEmpty(execution.getProcessDefinitionId())) {
                return ProcessType.BPMN;
            }
        }

        return ProcessType.UNKNOWN;
    }

    /**
     * Validates if a task is eligible for step back operation.
     * 验证任务是否符合回退操作的条件。
     * 
     * @param task The task to validate
     * @throws WorkflowStepBackException if validation fails
     */
    public static void validateTaskForStepBack(TaskEntity task) {
        if (task == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.TASK_NOT_FOUND,
                "Task cannot be null for step back operation. 回退操作的任务不能为空。"
            );
        }

        if (task.isDeleted()) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TASK_STATE,
                "Cannot step back deleted task. 无法回退已删除的任务。",
                task.getId(), task.getProcessInstanceId(), null
            );
        }

        if (task.isSuspended()) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TASK_STATE,
                "Cannot step back suspended task. 无法回退已暂停的任务。",
                task.getId(), task.getProcessInstanceId(), null
            );
        }

        // Check if task is from CMMN engine
        if (StringUtils.isNotEmpty(task.getScopeId()) && ScopeTypes.CMMN.equals(task.getScopeType())) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.UNSUPPORTED_PROCESS_TYPE,
                "CMMN tasks should be handled via CMMN engine for step back. CMMN任务的回退应通过CMMN引擎处理。",
                task.getId(), task.getProcessInstanceId(), null
            );
        }
    }

    /**
     * Validates if an execution is eligible for step back operation.
     * 验证执行是否符合回退操作的条件。
     * 
     * @param execution The execution to validate
     * @throws WorkflowStepBackException if validation fails
     */
    public static void validateExecutionForStepBack(ExecutionEntity execution) {
        if (execution == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.EXECUTION_NOT_FOUND,
                "Execution cannot be null for step back operation. 回退操作的执行不能为空。"
            );
        }

        if (!execution.isActive()) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TASK_STATE,
                "Cannot step back inactive execution. 无法回退非活动的执行。",
                null, execution.getProcessInstanceId(), execution.getActivityId()
            );
        }

        if (execution.isEnded()) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TASK_STATE,
                "Cannot step back ended execution. 无法回退已结束的执行。",
                null, execution.getProcessInstanceId(), execution.getActivityId()
            );
        }
    }

    /**
     * Validates if a target activity is valid for step back operation.
     * 验证目标活动是否对回退操作有效。
     * 
     * @param processDefinition The process definition
     * @param targetActivityId The target activity ID
     * @throws WorkflowStepBackException if validation fails
     */
    public static void validateTargetActivity(ProcessDefinitionEntity processDefinition, String targetActivityId) {
        if (StringUtils.isEmpty(targetActivityId)) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                "Target activity ID cannot be empty. 目标活动ID不能为空。"
            );
        }

        if (processDefinition == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "Process definition not found. 未找到流程定义。"
            );
        }

        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinition.getId());
        if (bpmnModel == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "BPMN model not found for process definition. 未找到流程定义的BPMN模型。"
            );
        }

        Process process = bpmnModel.getMainProcess();
        FlowElement targetElement = process.getFlowElement(targetActivityId, true);
        
        if (targetElement == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                "Target activity not found in process: " + targetActivityId + ". 在流程中未找到目标活动: " + targetActivityId + "。"
            );
        }

        // Additional validations for specific activity types
        if (targetElement instanceof UserTask) {
            LOGGER.debug("Target activity is a UserTask: {}", targetActivityId);
        } else {
            LOGGER.warn("Target activity is not a UserTask, step back may have unexpected behavior: {}", targetActivityId);
        }
    }

    /**
     * Finds all possible step back target activities for a given current activity.
     * 为给定的当前活动查找所有可能的回退目标活动。
     * 
     * @param processDefinition The process definition
     * @param currentActivityId The current activity ID
     * @return List of possible target activity IDs
     */
    public static List<String> findPossibleStepBackTargets(ProcessDefinitionEntity processDefinition, String currentActivityId) {
        if (processDefinition == null || StringUtils.isEmpty(currentActivityId)) {
            return Collections.emptyList();
        }

        try {
            BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinition.getId());
            if (bpmnModel == null) {
                return Collections.emptyList();
            }

            Process process = bpmnModel.getMainProcess();
            FlowElement currentElement = process.getFlowElement(currentActivityId, true);
            
            if (currentElement == null) {
                return Collections.emptyList();
            }

            Set<String> visitedElements = new HashSet<>();
            List<String> possibleTargets = new ArrayList<>();
            
            // Find all UserTask activities that can potentially be step back targets
            findPossibleTargetsRecursive(process, currentElement, visitedElements, possibleTargets);
            
            return possibleTargets;
        } catch (Exception e) {
            LOGGER.error("Error finding possible step back targets for activity {}: {}", currentActivityId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Recursively finds possible step back targets.
     * 递归查找可能的回退目标。
     */
    private static void findPossibleTargetsRecursive(Process process, FlowElement currentElement, 
                                                   Set<String> visitedElements, List<String> possibleTargets) {
        if (currentElement == null || visitedElements.contains(currentElement.getId())) {
            return;
        }
        
        visitedElements.add(currentElement.getId());

        // Check incoming sequence flows to find previous activities
        Collection<SequenceFlow> incomingFlows = process.findFlowElementsOfType(SequenceFlow.class);
        for (SequenceFlow flow : incomingFlows) {
            if (currentElement.getId().equals(flow.getTargetRef())) {
                FlowElement sourceElement = process.getFlowElement(flow.getSourceRef(), true);
                if (sourceElement instanceof UserTask && !possibleTargets.contains(sourceElement.getId())) {
                    possibleTargets.add(sourceElement.getId());
                }
                // Continue searching recursively
                findPossibleTargetsRecursive(process, sourceElement, visitedElements, possibleTargets);
            }
        }
    }

    /**
     * Checks if the current user has permission to perform step back operation.
     * 检查当前用户是否有权限执行回退操作。
     * 
     * @param task The task involved in step back
     * @param userId The user ID requesting the operation
     * @return true if user has permission, false otherwise
     */
    public static boolean hasStepBackPermission(TaskEntity task, String userId) {
        if (task == null || StringUtils.isEmpty(userId)) {
            return false;
        }

        // Check if user is assignee
        if (userId.equals(task.getAssignee())) {
            return true;
        }

        // Check if user is owner
        if (userId.equals(task.getOwner())) {
            return true;
        }

        // Additional permission checks can be added here
        // For example, checking group membership, roles, etc.
        
        return false;
    }

    /**
     * Creates a step back reason message with both English and Chinese.
     * 创建包含英文和中文的回退原因消息。
     * 
     * @param userId The user performing the step back
     * @param reason The reason for step back
     * @return Formatted step back message
     */
    public static String createStepBackMessage(String userId, String reason) {
        StringBuilder message = new StringBuilder();
        message.append("Step back by user: ").append(userId != null ? userId : "unknown");
        message.append(" | 用户回退: ").append(userId != null ? userId : "未知");
        
        if (StringUtils.isNotEmpty(reason)) {
            message.append(" | Reason: ").append(reason);
            message.append(" | 原因: ").append(reason);
        }
        
        return message.toString();
    }

    /**
     * Validates if step back operation is supported for the given process type.
     * 验证给定流程类型是否支持回退操作。
     * 
     * @param processType The process type to check
     * @throws WorkflowStepBackException if process type is not supported
     */
    public static void validateProcessTypeForStepBack(ProcessType processType) {
        switch (processType) {
            case BPMN:
                // BPMN processes are fully supported
                break;
            case CMMN:
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.UNSUPPORTED_PROCESS_TYPE,
                    "CMMN processes should use CMMN-specific step back operations. CMMN流程应使用CMMN特定的回退操作。"
                );
            case DMN:
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.UNSUPPORTED_PROCESS_TYPE,
                    "DMN processes do not support step back operations. DMN流程不支持回退操作。"
                );
            case UNKNOWN:
            default:
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.UNSUPPORTED_PROCESS_TYPE,
                    "Unknown process type does not support step back operations. 未知流程类型不支持回退操作。"
                );
        }
    }

    /**
     * Checks if the given task is part of a multi-instance activity.
     * 检查给定任务是否属于多实例活动。
     * 
     * @param task The task to check
     * @return true if task is part of multi-instance activity
     */
    public static boolean isMultiInstanceTask(TaskEntity task) {
        if (task == null || StringUtils.isEmpty(task.getExecutionId())) {
            return false;
        }

        try {
            CommandContext commandContext = CommandContextUtil.getCommandContext();
            ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext)
                .findById(task.getExecutionId());
            
            return execution != null && execution.isMultiInstanceRoot();
        } catch (Exception e) {
            LOGGER.error("Error checking if task is multi-instance: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if the given task is part of a subprocess.
     * 检查给定任务是否属于子流程。
     * 
     * @param task The task to check
     * @return true if task is part of subprocess
     */
    public static boolean isSubprocessTask(TaskEntity task) {
        if (task == null || StringUtils.isEmpty(task.getProcessDefinitionId())) {
            return false;
        }

        try {
            ProcessDefinitionEntity processDefinition = ProcessDefinitionUtil.getProcessDefinitionFromDatabase(task.getProcessDefinitionId());
            BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinition.getId());
            
            if (bpmnModel != null) {
                Process process = bpmnModel.getMainProcess();
                FlowElement taskElement = process.getFlowElement(task.getTaskDefinitionKey(), true);
                
                if (taskElement != null) {
                    org.flowable.bpmn.model.FlowElementsContainer parentContainer = taskElement.getParentContainer();
                    return parentContainer instanceof SubProcess;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error checking if task is in subprocess: {}", e.getMessage(), e);
        }
        
        return false;
    }
}