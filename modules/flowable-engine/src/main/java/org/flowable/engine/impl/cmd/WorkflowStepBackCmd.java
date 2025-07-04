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
package org.flowable.engine.impl.cmd;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.engine.impl.util.TaskHelper;
import org.flowable.engine.impl.util.WorkflowStepBackUtils;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for performing basic workflow step back operations.
 * 执行基本工作流回退操作的命令。
 * 
 * This command handles stepping back a task to its previous activity in the workflow.
 * 此命令处理将任务回退到工作流中的上一个活动。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackCmd implements Command<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackCmd.class);

    protected final String taskId;
    protected final String userId;
    protected final String reason;

    /**
     * Constructor for basic step back command.
     * 基本回退命令的构造函数。
     * 
     * @param taskId The ID of the task to step back
     * @param userId The user performing the step back operation
     * @param reason The reason for step back (optional)
     */
    public WorkflowStepBackCmd(String taskId, String userId, String reason) {
        this.taskId = taskId;
        this.userId = userId;
        this.reason = reason;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        LOGGER.info("Executing step back command for task: {} by user: {} with reason: {}", 
                   taskId, userId, reason);

        // Validate input parameters
        if (StringUtils.isEmpty(taskId)) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.TASK_NOT_FOUND,
                "Task ID cannot be empty. 任务ID不能为空。"
            );
        }

        // Get the task entity
        TaskEntity task = CommandContextUtil.getTaskService(commandContext).getTask(taskId);
        if (task == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.TASK_NOT_FOUND,
                "Task not found with ID: " + taskId + ". 未找到ID为 " + taskId + " 的任务。",
                taskId, null, null
            );
        }

        // Validate task for step back
        WorkflowStepBackUtils.validateTaskForStepBack(task);

        // Check permissions
        if (!WorkflowStepBackUtils.hasStepBackPermission(task, userId)) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PERMISSION_DENIED,
                "User " + userId + " does not have permission to step back task " + taskId + 
                ". 用户 " + userId + " 没有权限回退任务 " + taskId + "。",
                taskId, task.getProcessInstanceId(), null
            );
        }

        // Get the execution
        ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext)
            .findById(task.getExecutionId());
        if (execution == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.EXECUTION_NOT_FOUND,
                "Execution not found for task: " + taskId + ". 未找到任务的执行: " + taskId + "。",
                taskId, task.getProcessInstanceId(), null
            );
        }

        // Validate execution
        WorkflowStepBackUtils.validateExecutionForStepBack(execution);

        // Determine process type
        WorkflowStepBackUtils.ProcessType processType = WorkflowStepBackUtils.determineProcessType(task, execution);
        WorkflowStepBackUtils.validateProcessTypeForStepBack(processType);

        // Find the target activity to step back to
        String targetActivityId = findPreviousActivity(commandContext, task, execution);
        if (StringUtils.isEmpty(targetActivityId)) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                "Cannot find previous activity to step back to. 无法找到要回退到的上一个活动。",
                taskId, task.getProcessInstanceId(), null
            );
        }

        // Validate target activity
        ProcessDefinitionEntity processDefinition = ProcessDefinitionUtil.getProcessDefinitionFromDatabase(task.getProcessDefinitionId());
        WorkflowStepBackUtils.validateTargetActivity(processDefinition, targetActivityId);

        // Perform the step back operation
        performStepBack(commandContext, task, execution, targetActivityId);

        // Log the step back operation
        logStepBackOperation(task, targetActivityId, userId, reason);

        LOGGER.info("Successfully completed step back command for task: {} to activity: {}", 
                   taskId, targetActivityId);

        return null;
    }

    /**
     * Finds the previous activity in the workflow.
     * 在工作流中查找上一个活动。
     * 
     * @param commandContext The command context
     * @param task The current task
     * @param execution The current execution
     * @return The ID of the previous activity, or null if not found
     */
    protected String findPreviousActivity(CommandContext commandContext, TaskEntity task, ExecutionEntity execution) {
        try {
            ProcessDefinitionEntity processDefinition = ProcessDefinitionUtil.getProcessDefinitionFromDatabase(task.getProcessDefinitionId());
            BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processDefinition.getId());
            
            if (bpmnModel == null) {
                return null;
            }

            Process process = bpmnModel.getMainProcess();
            String currentActivityId = task.getTaskDefinitionKey();

            // Find incoming sequence flows to current activity
            List<SequenceFlow> incomingFlows = process.findFlowElementsOfType(SequenceFlow.class);
            for (SequenceFlow flow : incomingFlows) {
                if (currentActivityId.equals(flow.getTargetRef())) {
                    // Found an incoming flow, return the source activity
                    return flow.getSourceRef();
                }
            }

            return null;
        } catch (Exception e) {
            LOGGER.error("Error finding previous activity for task: {}. Error: {}", taskId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Performs the actual step back operation.
     * 执行实际的回退操作。
     * 
     * @param commandContext The command context
     * @param task The task to step back
     * @param execution The current execution
     * @param targetActivityId The target activity to step back to
     */
    protected void performStepBack(CommandContext commandContext, TaskEntity task, 
                                 ExecutionEntity execution, String targetActivityId) {
        try {
            // Create step back message
            String stepBackMessage = WorkflowStepBackUtils.createStepBackMessage(userId, reason);

            // Delete the current task
            TaskHelper.deleteTask(task, stepBackMessage, false, true, true);

            // Move the execution to the target activity using the proper Flowable approach
            ExecutionEntityImpl executionImpl = (ExecutionEntityImpl) execution;
            executionImpl.setActivityId(targetActivityId);
            
            // Update execution state
            CommandContextUtil.getExecutionEntityManager(commandContext).update(execution);

            // Continue execution from the target activity
            CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(execution);

            LOGGER.debug("Step back operation completed: moved execution from {} to {}", 
                        task.getTaskDefinitionKey(), targetActivityId);

        } catch (Exception e) {
            LOGGER.error("Error performing step back operation for task: {}. Error: {}", taskId, e.getMessage(), e);
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "Failed to perform step back operation: " + e.getMessage() + 
                ". 执行回退操作失败: " + e.getMessage() + "。",
                taskId, task.getProcessInstanceId(), targetActivityId, e
            );
        }
    }

    /**
     * Logs the step back operation for audit purposes.
     * 为审计目的记录回退操作。
     * 
     * @param task The task that was stepped back
     * @param targetActivityId The target activity
     * @param userId The user who performed the operation
     * @param reason The reason for step back
     */
    protected void logStepBackOperation(TaskEntity task, String targetActivityId, String userId, String reason) {
        try {
            StringBuilder logMessage = new StringBuilder();
            logMessage.append("Workflow step back operation performed: ");
            logMessage.append("taskId=").append(task.getId());
            logMessage.append(", processInstanceId=").append(task.getProcessInstanceId());
            logMessage.append(", fromActivity=").append(task.getTaskDefinitionKey());
            logMessage.append(", toActivity=").append(targetActivityId);
            logMessage.append(", userId=").append(userId);
            if (StringUtils.isNotEmpty(reason)) {
                logMessage.append(", reason=").append(reason);
            }

            LOGGER.info(logMessage.toString());

            // Additional audit logging could be added here
            // For example, writing to an audit table or sending audit events

        } catch (Exception e) {
            LOGGER.warn("Failed to log step back operation: {}", e.getMessage());
            // Don't fail the operation just because logging failed
        }
    }
}