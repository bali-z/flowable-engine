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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command for stepping back a task with variable modifications.
 * 回退任务并修改变量的命令。
 * 
 * This command extends the step back to activity functionality to also allow
 * setting process variables and local variables during the step back operation.
 * 此命令扩展了回退到活动的功能，还允许在回退操作期间设置流程变量和本地变量。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackWithVariablesCmd extends WorkflowStepBackToActivityCmd {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackWithVariablesCmd.class);

    protected final Map<String, Object> variables;
    protected final Map<String, Object> localVariables;

    /**
     * Constructor for step back with variables command.
     * 回退并修改变量命令的构造函数。
     * 
     * @param taskId The ID of the task to step back
     * @param targetActivityId The ID of the target activity to step back to (optional)
     * @param userId The user performing the step back operation
     * @param reason The reason for step back (optional)
     * @param variables Process variables to set during step back
     * @param localVariables Local variables to set during step back
     */
    public WorkflowStepBackWithVariablesCmd(String taskId, String targetActivityId, String userId, String reason,
                                           Map<String, Object> variables, Map<String, Object> localVariables) {
        super(taskId, targetActivityId, userId, reason);
        this.variables = variables != null ? variables : new HashMap<>();
        this.localVariables = localVariables != null ? localVariables : new HashMap<>();
    }

    @Override
    public Void execute(CommandContext commandContext) {
        LOGGER.info("Executing step back with variables command for task: {} to activity: {} by user: {} with reason: {}, variables: {}, localVariables: {}", 
                   taskId, targetActivityId, userId, reason, variables.size(), localVariables.size());

        // If no target activity is specified, we need to find the previous one
        if (StringUtils.isEmpty(targetActivityId)) {
            // Get the task to find the previous activity
            TaskEntity task = CommandContextUtil.getTaskService(commandContext).getTask(taskId);
            if (task == null) {
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.TASK_NOT_FOUND,
                    "Task not found with ID: " + taskId + ". 未找到ID为 " + taskId + " 的任务。",
                    taskId, null, null
                );
            }

            ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext)
                .findById(task.getExecutionId());
            if (execution == null) {
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.EXECUTION_NOT_FOUND,
                    "Execution not found for task: " + taskId + ". 未找到任务的执行: " + taskId + "。",
                    taskId, task.getProcessInstanceId(), null
                );
            }

            // Find the previous activity
            String previousActivity = findPreviousActivity(commandContext, task, execution);
            if (StringUtils.isEmpty(previousActivity)) {
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                    "Cannot find previous activity to step back to. 无法找到要回退到的上一个活动。",
                    taskId, task.getProcessInstanceId(), null
                );
            }
            
            // Create a new command with the found target activity
            WorkflowStepBackWithVariablesCmd newCommand = new WorkflowStepBackWithVariablesCmd(
                taskId, previousActivity, userId, reason, variables, localVariables);
            return newCommand.execute(commandContext);
        }

        // Set variables before performing the step back
        setVariablesBeforeStepBack(commandContext);

        // Perform the step back operation
        Void result = super.execute(commandContext);

        // Set variables after performing the step back (if needed)
        setVariablesAfterStepBack(commandContext);

        LOGGER.info("Successfully completed step back with variables command for task: {} to activity: {}", 
                   taskId, targetActivityId);

        return result;
    }

    /**
     * Sets variables before performing the step back operation.
     * 在执行回退操作之前设置变量。
     * 
     * @param commandContext The command context
     */
    protected void setVariablesBeforeStepBack(CommandContext commandContext) {
        if (variables.isEmpty() && localVariables.isEmpty()) {
            return;
        }

        try {
            LOGGER.debug("Setting variables before step back for task: {}. Variables: {}, LocalVariables: {}", 
                        taskId, variables.size(), localVariables.size());

            // Get the task and execution
            TaskEntity task = CommandContextUtil.getTaskService(commandContext).getTask(taskId);
            if (task == null) {
                LOGGER.warn("Task not found when setting variables before step back: {}", taskId);
                return;
            }

            ExecutionEntity execution = CommandContextUtil.getExecutionEntityManager(commandContext)
                .findById(task.getExecutionId());
            if (execution == null) {
                LOGGER.warn("Execution not found when setting variables before step back: {}", taskId);
                return;
            }

            // Set process variables
            if (!variables.isEmpty()) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String variableName = entry.getKey();
                    Object variableValue = entry.getValue();
                    
                    if (StringUtils.isNotEmpty(variableName)) {
                        execution.setVariable(variableName, variableValue);
                        LOGGER.debug("Set process variable: {} = {} for task: {}", variableName, variableValue, taskId);
                    }
                }
            }

            // Set local variables (task-specific variables)
            if (!localVariables.isEmpty()) {
                for (Map.Entry<String, Object> entry : localVariables.entrySet()) {
                    String variableName = entry.getKey();
                    Object variableValue = entry.getValue();
                    
                    if (StringUtils.isNotEmpty(variableName)) {
                        task.setVariableLocal(variableName, variableValue);
                        LOGGER.debug("Set local variable: {} = {} for task: {}", variableName, variableValue, taskId);
                    }
                }
            }

            LOGGER.debug("Successfully set variables before step back for task: {}", taskId);

        } catch (Exception e) {
            LOGGER.error("Error setting variables before step back for task: {}. Error: {}", taskId, e.getMessage(), e);
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "Failed to set variables before step back: " + e.getMessage() + 
                ". 回退前设置变量失败: " + e.getMessage() + "。",
                taskId, null, targetActivityId, e
            );
        }
    }

    /**
     * Sets variables after performing the step back operation.
     * 在执行回退操作之后设置变量。
     * 
     * This method can be used to set variables on the new task that is created
     * as a result of the step back operation.
     * 此方法可用于在回退操作后创建的新任务上设置变量。
     * 
     * @param commandContext The command context
     */
    protected void setVariablesAfterStepBack(CommandContext commandContext) {
        // This method is called after the step back operation is complete
        // It can be used to set additional variables on the new task or execution
        // For now, we don't need additional variable setting after step back
        
        LOGGER.debug("Step back with variables completed for task: {} to activity: {}", taskId, targetActivityId);
        
        // Additional post-step-back variable logic could be added here if needed
        // For example:
        // - Setting audit variables
        // - Updating process state variables
        // - Sending notifications with variable information
    }

    /**
     * Enhanced step back operation that includes variable handling.
     * 包含变量处理的增强回退操作。
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
            LOGGER.debug("Performing step back with variables from {} to {} for task {}", 
                        task.getTaskDefinitionKey(), targetActivityId, taskId);

            // Create enhanced step back message that includes variable information
            String stepBackMessage = createEnhancedStepBackMessage();

            // Handle variables in the step back context
            handleVariablesInStepBack(commandContext, task, execution);

            // Perform the actual step back operation
            super.performStepBack(commandContext, task, execution, targetActivityId);

            LOGGER.debug("Step back with variables completed successfully for task: {}", taskId);

        } catch (Exception e) {
            LOGGER.error("Error performing step back with variables for task: {}. Error: {}", 
                        taskId, e.getMessage(), e);
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.PROCESS_CONFIGURATION_ERROR,
                "Failed to perform step back with variables: " + e.getMessage() + 
                ". 执行带变量的回退操作失败: " + e.getMessage() + "。",
                taskId, task.getProcessInstanceId(), targetActivityId, e
            );
        }
    }

    /**
     * Creates an enhanced step back message that includes variable information.
     * 创建包含变量信息的增强回退消息。
     * 
     * @return Enhanced step back message
     */
    protected String createEnhancedStepBackMessage() {
        StringBuilder message = new StringBuilder();
        message.append("Step back with variables by user: ").append(userId != null ? userId : "unknown");
        message.append(" | 用户带变量回退: ").append(userId != null ? userId : "未知");
        
        if (StringUtils.isNotEmpty(reason)) {
            message.append(" | Reason: ").append(reason);
            message.append(" | 原因: ").append(reason);
        }
        
        if (!variables.isEmpty()) {
            message.append(" | Process variables: ").append(variables.size());
            message.append(" | 流程变量: ").append(variables.size());
        }
        
        if (!localVariables.isEmpty()) {
            message.append(" | Local variables: ").append(localVariables.size());
            message.append(" | 本地变量: ").append(localVariables.size());
        }
        
        return message.toString();
    }

    /**
     * Handles variables in the context of the step back operation.
     * 在回退操作的上下文中处理变量。
     * 
     * @param commandContext The command context
     * @param task The task being stepped back
     * @param execution The execution being stepped back
     */
    protected void handleVariablesInStepBack(CommandContext commandContext, TaskEntity task, ExecutionEntity execution) {
        // Log variable changes for audit purposes
        logVariableChanges(task, execution);
        
        // Validate variable values if needed
        validateVariables();
        
        // Additional variable handling logic can be added here
    }

    /**
     * Logs variable changes for audit purposes.
     * 为审计目的记录变量更改。
     * 
     * @param task The task being stepped back
     * @param execution The execution being stepped back
     */
    protected void logVariableChanges(TaskEntity task, ExecutionEntity execution) {
        try {
            if (!variables.isEmpty() || !localVariables.isEmpty()) {
                StringBuilder logMessage = new StringBuilder();
                logMessage.append("Variable changes during step back operation: ");
                logMessage.append("taskId=").append(task.getId());
                logMessage.append(", processInstanceId=").append(task.getProcessInstanceId());
                logMessage.append(", userId=").append(userId);
                
                if (!variables.isEmpty()) {
                    logMessage.append(", processVariables=").append(variables.keySet());
                }
                
                if (!localVariables.isEmpty()) {
                    logMessage.append(", localVariables=").append(localVariables.keySet());
                }
                
                LOGGER.info(logMessage.toString());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to log variable changes: {}", e.getMessage());
        }
    }

    /**
     * Validates variable values before setting them.
     * 在设置变量之前验证变量值。
     */
    protected void validateVariables() {
        // Validate process variables
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            validateVariable(entry.getKey(), entry.getValue(), false);
        }
        
        // Validate local variables
        for (Map.Entry<String, Object> entry : localVariables.entrySet()) {
            validateVariable(entry.getKey(), entry.getValue(), true);
        }
    }

    /**
     * Validates a single variable.
     * 验证单个变量。
     * 
     * @param name The variable name
     * @param value The variable value
     * @param isLocal Whether this is a local variable
     */
    protected void validateVariable(String name, Object value, boolean isLocal) {
        if (StringUtils.isEmpty(name)) {
            throw new WorkflowStepBackException(
                WorkflowStepBackException.StepBackErrorCode.INVALID_TARGET_NODE,
                "Variable name cannot be empty. 变量名不能为空。",
                taskId, null, targetActivityId
            );
        }
        
        // Additional variable validation logic can be added here
        // For example:
        // - Check for reserved variable names
        // - Validate variable value types
        // - Check business rule constraints
        
        LOGGER.debug("Variable validated: {} = {} (local: {})", name, value, isLocal);
    }
}