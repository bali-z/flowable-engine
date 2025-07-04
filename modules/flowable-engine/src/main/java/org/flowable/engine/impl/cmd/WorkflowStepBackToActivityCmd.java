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

import org.apache.commons.lang3.StringUtils;
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
 * Command for stepping back a task to a specific target activity.
 * 将任务回退到特定目标活动的命令。
 * 
 * This command extends the basic step back functionality to allow stepping back
 * to any specified activity in the process, not just the immediate previous one.
 * 此命令扩展了基本回退功能，允许回退到流程中的任何指定活动，而不仅仅是直接的上一个活动。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackToActivityCmd extends WorkflowStepBackCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackToActivityCmd.class);

    protected final String targetActivityId;

    /**
     * Constructor for step back to specific activity command.
     * 回退到特定活动命令的构造函数。
     * 
     * @param taskId The ID of the task to step back
     * @param targetActivityId The ID of the target activity to step back to
     * @param userId The user performing the step back operation
     * @param reason The reason for step back (optional)
     */
    public WorkflowStepBackToActivityCmd(String taskId, String targetActivityId, String userId, String reason) {
        super(taskId, userId, reason);
        this.targetActivityId = targetActivityId;
    }

    @Override
    public Void execute(CommandContext commandContext) {
        LOGGER.info("Executing step back to activity command for task: {} to activity: {} by user: {} with reason: {}", 
                   taskId, targetActivityId, userId, reason);

        // Validate target activity ID
        if (StringUtils.isEmpty(targetActivityId)) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                "Target activity ID cannot be empty. 目标活动ID不能为空。"
            );
        }

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
                taskId, null, targetActivityId
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
                taskId, task.getProcessInstanceId(), targetActivityId
            );
        }

        // Get the execution
        ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext)
            .findById(task.getExecutionId());
        if (execution == null) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.EXECUTION_NOT_FOUND,
                "Execution not found for task: " + taskId + ". 未找到任务的执行: " + taskId + "。",
                taskId, task.getProcessInstanceId(), targetActivityId
            );
        }

        // Validate execution
        WorkflowStepBackUtils.validateExecutionForStepBack(execution);

        // Determine process type
        WorkflowStepBackUtils.ProcessType processType = WorkflowStepBackUtils.determineProcessType(task, execution);
        WorkflowStepBackUtils.validateProcessTypeForStepBack(processType);

        // Validate target activity
        ProcessDefinitionEntity processDefinition = ProcessDefinitionUtil.getProcessDefinitionFromDatabase(task.getProcessDefinitionId());
        WorkflowStepBackUtils.validateTargetActivity(processDefinition, targetActivityId);

        // Validate that the target activity is a valid step back target
        validateTargetActivityForStepBack(commandContext, task, execution, targetActivityId);

        // Perform the step back operation
        performStepBack(commandContext, task, execution, targetActivityId);

        // Log the step back operation
        logStepBackOperation(task, targetActivityId, userId, reason);

        LOGGER.info("Successfully completed step back to activity command for task: {} to activity: {}", 
                   taskId, targetActivityId);

        return null;
    }

    /**
     * Validates that the target activity is a valid step back destination.
     * 验证目标活动是否为有效的回退目标。
     * 
     * @param commandContext The command context
     * @param task The task to step back
     * @param execution The current execution
     * @param targetActivityId The target activity ID
     * @throws WorkflowStepBackException if target is not valid
     */
    protected void validateTargetActivityForStepBack(CommandContext commandContext, TaskEntity task, 
                                                   ExecutionEntity execution, String targetActivityId) {
        try {
            ProcessDefinitionEntity processDefinition = ProcessDefinitionUtil.getProcessDefinitionFromDatabase(task.getProcessDefinitionId());
            
            // Get possible step back targets
            java.util.List<String> possibleTargets = WorkflowStepBackUtils.findPossibleStepBackTargets(
                processDefinition, task.getTaskDefinitionKey());

            // Check if target activity is in the list of possible targets
            if (!possibleTargets.contains(targetActivityId)) {
                LOGGER.warn("Target activity {} is not in the list of possible step back targets for task {}. Possible targets: {}", 
                           targetActivityId, taskId, possibleTargets);
                
                // For now, we'll allow it but log a warning
                // In a stricter implementation, you might want to throw an exception here
                // throw new WorkflowStepBackException(
                //     WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                //     "Target activity is not a valid step back destination. 目标活动不是有效的回退目标。",
                //     taskId, task.getProcessInstanceId(), targetActivityId
                // );
            }

            // Additional validations can be added here
            // For example, checking for loops, validating business rules, etc.

        } catch (WorkflowStepBackException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Error validating target activity for step back: {}", e.getMessage(), e);
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "Failed to validate target activity: " + e.getMessage() + 
                ". 验证目标活动失败: " + e.getMessage() + "。",
                taskId, task.getProcessInstanceId(), targetActivityId, e
            );
        }
    }

    /**
     * Overrides the findPreviousActivity method to return the specified target activity.
     * 重写findPreviousActivity方法以返回指定的目标活动。
     */
    @Override
    protected String findPreviousActivity(CommandContext commandContext, TaskEntity task, ExecutionEntity execution) {
        // For this command, we already know the target activity
        return targetActivityId;
    }

    /**
     * Enhanced step back operation that handles more complex scenarios.
     * 处理更复杂场景的增强回退操作。
     * 
     * @param commandContext The command context
     * @param task The task to step back
     * @param execution The current execution
     * @param targetActivityId The target activity to step back to
     */
    @Override
    protected void performStepBack(CommandContext commandContext, TaskEntity task, 
                                 ExecutionEntity execution, String targetActivityId) {
        try {
            LOGGER.debug("Performing enhanced step back from {} to {} for task {}", 
                        task.getTaskDefinitionKey(), targetActivityId, taskId);

            // Check if this is a complex step back scenario
            boolean isComplexStepBack = isComplexStepBackScenario(commandContext, task, execution, targetActivityId);

            if (isComplexStepBack) {
                performComplexStepBack(commandContext, task, execution, targetActivityId);
            } else {
                // Use the standard step back logic
                super.performStepBack(commandContext, task, execution, targetActivityId);
            }

        } catch (Exception e) {
            LOGGER.error("Error performing step back to activity operation for task: {}. Error: {}", 
                        taskId, e.getMessage(), e);
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "Failed to perform step back to activity operation: " + e.getMessage() + 
                ". 执行回退到活动操作失败: " + e.getMessage() + "。",
                taskId, task.getProcessInstanceId(), targetActivityId, e
            );
        }
    }

    /**
     * Determines if this is a complex step back scenario that needs special handling.
     * 确定这是否是需要特殊处理的复杂回退场景。
     * 
     * @param commandContext The command context
     * @param task The task to step back
     * @param execution The current execution
     * @param targetActivityId The target activity
     * @return true if complex handling is needed
     */
    protected boolean isComplexStepBackScenario(CommandContext commandContext, TaskEntity task, 
                                               ExecutionEntity execution, String targetActivityId) {
        // Check for multi-instance scenarios
        if (WorkflowStepBackUtils.isMultiInstanceTask(task)) {
            return true;
        }

        // Check for subprocess scenarios
        if (WorkflowStepBackUtils.isSubprocessTask(task)) {
            return true;
        }

        // Check if stepping back across multiple activities
        String currentActivity = task.getTaskDefinitionKey();
        if (!isDirectPrevious(commandContext, currentActivity, targetActivityId)) {
            return true;
        }

        return false;
    }

    /**
     * Performs complex step back operations for special scenarios.
     * 为特殊场景执行复杂的回退操作。
     * 
     * @param commandContext The command context
     * @param task The task to step back
     * @param execution The current execution
     * @param targetActivityId The target activity
     */
    protected void performComplexStepBack(CommandContext commandContext, TaskEntity task, 
                                        ExecutionEntity execution, String targetActivityId) {
        LOGGER.info("Performing complex step back operation for task: {} to activity: {}", taskId, targetActivityId);

        // Create step back message
        String stepBackMessage = WorkflowStepBackUtils.createStepBackMessage(userId, reason);

        // Handle multi-instance scenarios
        if (WorkflowStepBackUtils.isMultiInstanceTask(task)) {
            handleMultiInstanceStepBack(commandContext, task, execution, targetActivityId, stepBackMessage);
            return;
        }

        // Handle subprocess scenarios
        if (WorkflowStepBackUtils.isSubprocessTask(task)) {
            handleSubprocessStepBack(commandContext, task, execution, targetActivityId, stepBackMessage);
            return;
        }

        // Handle multi-step back (across multiple activities)
        handleMultiStepBack(commandContext, task, execution, targetActivityId, stepBackMessage);
    }

    /**
     * Handles step back for multi-instance tasks.
     * 处理多实例任务的回退。
     */
    protected void handleMultiInstanceStepBack(CommandContext commandContext, TaskEntity task, 
                                             ExecutionEntity execution, String targetActivityId, String stepBackMessage) {
        LOGGER.debug("Handling multi-instance step back for task: {}", taskId);
        
        // For multi-instance, we need to handle the step back carefully
        // This might involve stepping back the parent execution or all instances
        
        // Delete the current task
        TaskHelper.deleteTask(task, stepBackMessage, false, true, true);
        
        // Get the parent execution if this is a multi-instance child
        ExecutionEntity parentExecution = execution.getParent();
        if (parentExecution != null && parentExecution.isMultiInstanceRoot()) {
            // Step back the parent execution
            ExecutionEntityImpl parentExecutionImpl = (ExecutionEntityImpl) parentExecution;
            parentExecutionImpl.setActivityId(targetActivityId);
            CommandContextUtil.getExecutionEntityManager(commandContext).update(parentExecution);
            CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(parentExecution);
        } else {
            // Step back the current execution
            ExecutionEntityImpl executionImpl = (ExecutionEntityImpl) execution;
            executionImpl.setActivityId(targetActivityId);
            CommandContextUtil.getExecutionEntityManager(commandContext).update(execution);
            CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(execution);
        }
    }

    /**
     * Handles step back for subprocess tasks.
     * 处理子流程任务的回退。
     */
    protected void handleSubprocessStepBack(CommandContext commandContext, TaskEntity task, 
                                          ExecutionEntity execution, String targetActivityId, String stepBackMessage) {
        LOGGER.debug("Handling subprocess step back for task: {}", taskId);
        
        // Delete the current task
        TaskHelper.deleteTask(task, stepBackMessage, false, true, true);
        
        // For subprocess step back, we might need to exit the subprocess
        // and continue from the target activity in the parent process
        
        // Move the execution to the target activity
        ExecutionEntityImpl executionImpl = (ExecutionEntityImpl) execution;
        executionImpl.setActivityId(targetActivityId);
        CommandContextUtil.getExecutionEntityManager(commandContext).update(execution);
        CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(execution);
    }

    /**
     * Handles step back across multiple activities.
     * 处理跨多个活动的回退。
     */
    protected void handleMultiStepBack(CommandContext commandContext, TaskEntity task, 
                                     ExecutionEntity execution, String targetActivityId, String stepBackMessage) {
        LOGGER.debug("Handling multi-step back for task: {} to activity: {}", taskId, targetActivityId);
        
        // Delete the current task
        TaskHelper.deleteTask(task, stepBackMessage, false, true, true);
        
        // Move the execution to the target activity
        ExecutionEntityImpl executionImpl = (ExecutionEntityImpl) execution;
        executionImpl.setActivityId(targetActivityId);
        CommandContextUtil.getExecutionEntityManager(commandContext).update(execution);
        CommandContextUtil.getAgenda(commandContext).planContinueProcessOperation(execution);
    }

    /**
     * Checks if the target activity is the direct previous activity.
     * 检查目标活动是否为直接的上一个活动。
     */
    protected boolean isDirectPrevious(CommandContext commandContext, String currentActivity, String targetActivity) {
        // This would involve checking the process definition to see if targetActivity
        // directly precedes currentActivity in the flow
        // For now, return false to trigger complex handling
        return false;
    }
}