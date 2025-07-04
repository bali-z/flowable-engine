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

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.flowable.common.engine.impl.service.CommonEngineServiceImpl;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.cmd.WorkflowStepBackCmd;
import org.flowable.engine.impl.cmd.WorkflowStepBackException;
import org.flowable.engine.impl.cmd.WorkflowStepBackToActivityCmd;
import org.flowable.engine.impl.cmd.WorkflowStepBackWithVariablesCmd;
import org.flowable.engine.impl.util.WorkflowStepBackUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for workflow step back functionality.
 * 工作流回退功能的服务类。
 * 
 * This service provides comprehensive step back operations for Flowable workflows,
 * including support for single tasks, multi-instance tasks, and subprocess scenarios.
 * 此服务为Flowable工作流提供全面的回退操作，包括对单任务、多实例任务和子流程场景的支持。
 * 
 * Thread Safety: This service is thread-safe using ReentrantReadWriteLock.
 * 线程安全性：此服务使用ReentrantReadWriteLock确保线程安全。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackService extends CommonEngineServiceImpl<ProcessEngineConfigurationImpl> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackService.class);

    // Thread safety lock for concurrent operations
    // 并发操作的线程安全锁
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public WorkflowStepBackService(ProcessEngineConfigurationImpl processEngineConfiguration) {
        super(processEngineConfiguration);
    }

    /**
     * Performs a simple step back operation for a task to its previous activity.
     * 为任务执行简单的回退操作到其上一个活动。
     * 
     * This method steps back a task to its immediate previous activity in the process flow.
     * 此方法将任务回退到流程流中的上一个活动。
     * 
     * @param taskId The ID of the task to step back
     * @param userId The user performing the step back operation
     * @param reason The reason for stepping back (optional)
     * @throws WorkflowStepBackException if step back operation fails
     */
    public void stepBackTask(String taskId, String userId, String reason) {
        lock.readLock().lock();
        try {
            LOGGER.info("Starting step back operation for task: {} by user: {} with reason: {}", 
                       taskId, userId, reason);

            WorkflowStepBackCmd command = new WorkflowStepBackCmd(taskId, userId, reason);
            commandExecutor.execute(command);

            LOGGER.info("Successfully completed step back operation for task: {}", taskId);
        } catch (Exception e) {
            LOGGER.error("Failed to step back task: {} by user: {}. Error: {}", 
                        taskId, userId, e.getMessage(), e);
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Steps back a task to a specific target activity.
     * 将任务回退到指定的目标活动。
     * 
     * This method allows stepping back to any previous activity in the process,
     * not just the immediate previous one.
     * 此方法允许回退到流程中的任何先前活动，而不仅仅是直接的上一个活动。
     * 
     * @param taskId The ID of the task to step back
     * @param targetActivityId The ID of the target activity to step back to
     * @param userId The user performing the step back operation
     * @param reason The reason for stepping back (optional)
     * @throws WorkflowStepBackException if step back operation fails
     */
    public void stepBackToActivity(String taskId, String targetActivityId, String userId, String reason) {
        lock.readLock().lock();
        try {
            LOGGER.info("Starting step back to activity operation for task: {} to activity: {} by user: {} with reason: {}", 
                       taskId, targetActivityId, userId, reason);

            WorkflowStepBackToActivityCmd command = new WorkflowStepBackToActivityCmd(
                taskId, targetActivityId, userId, reason);
            commandExecutor.execute(command);

            LOGGER.info("Successfully completed step back to activity operation for task: {} to activity: {}", 
                       taskId, targetActivityId);
        } catch (Exception e) {
            LOGGER.error("Failed to step back task: {} to activity: {} by user: {}. Error: {}", 
                        taskId, targetActivityId, userId, e.getMessage(), e);
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Steps back a task with variable modifications.
     * 回退任务并修改变量。
     * 
     * This method allows stepping back while also modifying process variables,
     * which can be useful for correcting data or adjusting the process state.
     * 此方法允许在回退的同时修改流程变量，这对于纠正数据或调整流程状态很有用。
     * 
     * @param taskId The ID of the task to step back
     * @param targetActivityId The ID of the target activity to step back to (optional)
     * @param userId The user performing the step back operation
     * @param reason The reason for stepping back (optional)
     * @param variables Process variables to set during step back
     * @param localVariables Local variables to set during step back
     * @throws WorkflowStepBackException if step back operation fails
     */
    public void stepBackWithVariables(String taskId, String targetActivityId, String userId, 
                                    String reason, Map<String, Object> variables, 
                                    Map<String, Object> localVariables) {
        lock.readLock().lock();
        try {
            LOGGER.info("Starting step back with variables operation for task: {} to activity: {} by user: {} with reason: {}", 
                       taskId, targetActivityId, userId, reason);

            WorkflowStepBackWithVariablesCmd command = new WorkflowStepBackWithVariablesCmd(
                taskId, targetActivityId, userId, reason, variables, localVariables);
            commandExecutor.execute(command);

            LOGGER.info("Successfully completed step back with variables operation for task: {} to activity: {}", 
                       taskId, targetActivityId);
        } catch (Exception e) {
            LOGGER.error("Failed to step back task: {} with variables to activity: {} by user: {}. Error: {}", 
                        taskId, targetActivityId, userId, e.getMessage(), e);
            throw e;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Steps back multiple tasks in a batch operation.
     * 批量回退多个任务。
     * 
     * This method is useful for stepping back multiple related tasks,
     * such as tasks in a parallel gateway or multi-instance scenario.
     * 此方法对于回退多个相关任务很有用，例如并行网关或多实例场景中的任务。
     * 
     * @param taskIds List of task IDs to step back
     * @param targetActivityId The ID of the target activity to step back to (optional)
     * @param userId The user performing the step back operation
     * @param reason The reason for stepping back (optional)
     * @throws WorkflowStepBackException if any step back operation fails
     */
    public void batchStepBack(List<String> taskIds, String targetActivityId, String userId, String reason) {
        lock.writeLock().lock();
        try {
            LOGGER.info("Starting batch step back operation for {} tasks by user: {} with reason: {}", 
                       taskIds.size(), userId, reason);

            int successCount = 0;
            int failureCount = 0;

            for (String taskId : taskIds) {
                try {
                    if (targetActivityId != null) {
                        stepBackToActivity(taskId, targetActivityId, userId, reason);
                    } else {
                        stepBackTask(taskId, userId, reason);
                    }
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    LOGGER.error("Failed to step back task in batch: {}. Error: {}", taskId, e.getMessage());
                    // Continue with other tasks instead of failing completely
                }
            }

            LOGGER.info("Batch step back completed. Success: {}, Failures: {}", successCount, failureCount);

            if (failureCount > 0) {
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.MULTIPLE_INSTANCE_ERROR,
                    String.format("Batch step back completed with %d failures out of %d tasks. " +
                                "批量回退完成，%d个任务中有%d个失败。", 
                                failureCount, taskIds.size(), failureCount, taskIds.size())
                );
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Steps back a multi-instance task.
     * 回退多实例任务。
     * 
     * This method handles the complexity of stepping back tasks that are part of
     * a multi-instance activity, ensuring proper coordination.
     * 此方法处理回退多实例活动中任务的复杂性，确保适当的协调。
     * 
     * @param taskId The ID of the multi-instance task to step back
     * @param userId The user performing the step back operation
     * @param reason The reason for stepping back (optional)
     * @param stepBackAllInstances Whether to step back all instances or just this one
     * @throws WorkflowStepBackException if step back operation fails
     */
    public void stepBackMultiInstanceTask(String taskId, String userId, String reason, 
                                        boolean stepBackAllInstances) {
        lock.writeLock().lock();
        try {
            LOGGER.info("Starting multi-instance step back operation for task: {} by user: {} with reason: {}, stepBackAll: {}", 
                       taskId, userId, reason, stepBackAllInstances);

            // First validate the task
            if (!WorkflowStepBackUtils.isMultiInstanceTask(
                    commandExecutor.execute(new GetTaskEntityCmd(taskId)))) {
                throw new WorkflowStepBackException(
                    WorkflowStepBackException.StepBackErrorCode.MULTIPLE_INSTANCE_ERROR,
                    "Task is not part of a multi-instance activity. 任务不属于多实例活动。",
                    taskId, null, null
                );
            }

            if (stepBackAllInstances) {
                // Get all related multi-instance tasks and step them back
                List<String> relatedTaskIds = findRelatedMultiInstanceTasks(taskId);
                batchStepBack(relatedTaskIds, null, userId, reason);
            } else {
                // Step back only this specific instance
                stepBackTask(taskId, userId, reason);
            }

            LOGGER.info("Successfully completed multi-instance step back operation for task: {}", taskId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Steps back a subprocess task.
     * 回退子流程任务。
     * 
     * This method handles stepping back tasks that are part of a subprocess,
     * ensuring proper handling of subprocess boundaries.
     * 此方法处理回退子流程中任务的操作，确保正确处理子流程边界。
     * 
     * @param taskId The ID of the subprocess task to step back
     * @param userId The user performing the step back operation
     * @param reason The reason for stepping back (optional)
     * @param stepBackToMainProcess Whether to step back to the main process
     * @throws WorkflowStepBackException if step back operation fails
     */
    public void stepBackSubprocessTask(String taskId, String userId, String reason, 
                                     boolean stepBackToMainProcess) {
        lock.writeLock().lock();
        try {
            LOGGER.info("Starting subprocess step back operation for task: {} by user: {} with reason: {}, stepBackToMain: {}", 
                       taskId, userId, reason, stepBackToMainProcess);

            if (stepBackToMainProcess) {
                // Find the parent activity in the main process and step back to it
                String parentActivityId = findParentProcessActivity(taskId);
                if (parentActivityId != null) {
                    stepBackToActivity(taskId, parentActivityId, userId, reason);
                } else {
                    throw new WorkflowStepBackException(
                        WorkflowStepBackException.StepBackErrorCode.SUBPROCESS_ERROR,
                        "Cannot find parent activity to step back to. 无法找到要回退到的父活动。",
                        taskId, null, null
                    );
                }
            } else {
                // Step back within the subprocess
                stepBackTask(taskId, userId, reason);
            }

            LOGGER.info("Successfully completed subprocess step back operation for task: {}", taskId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets all possible step back target activities for a given task.
     * 获取给定任务的所有可能回退目标活动。
     * 
     * @param taskId The ID of the task
     * @return List of possible target activity IDs for step back
     * @throws WorkflowStepBackException if task is not found or invalid
     */
    public List<String> getPossibleStepBackTargets(String taskId) {
        lock.readLock().lock();
        try {
            LOGGER.debug("Finding possible step back targets for task: {}", taskId);

            GetPossibleStepBackTargetsCmd command = new GetPossibleStepBackTargetsCmd(taskId);
            List<String> targets = commandExecutor.execute(command);

            LOGGER.debug("Found {} possible step back targets for task: {}", targets.size(), taskId);
            return targets;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validates if a step back operation is possible for the given task.
     * 验证给定任务是否可以执行回退操作。
     * 
     * @param taskId The ID of the task to validate
     * @param targetActivityId The target activity ID (optional)
     * @param userId The user requesting the operation
     * @return ValidationResult containing validation status and messages
     */
    public StepBackValidationResult validateStepBack(String taskId, String targetActivityId, String userId) {
        lock.readLock().lock();
        try {
            LOGGER.debug("Validating step back operation for task: {} to activity: {} by user: {}", 
                        taskId, targetActivityId, userId);

            ValidateStepBackCmd command = new ValidateStepBackCmd(taskId, targetActivityId, userId);
            return commandExecutor.execute(command);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if the current configuration supports step back operations.
     * 检查当前配置是否支持回退操作。
     * 
     * @return true if step back is supported, false otherwise
     */
    public boolean isStepBackSupported() {
        // This can be configured based on engine configuration
        return configuration != null;
    }

    // Helper methods - 辅助方法

    /**
     * Finds related multi-instance tasks for a given task ID.
     * 查找给定任务ID的相关多实例任务。
     */
    private List<String> findRelatedMultiInstanceTasks(String taskId) {
        FindRelatedMultiInstanceTasksCmd command = new FindRelatedMultiInstanceTasksCmd(taskId);
        return commandExecutor.execute(command);
    }

    /**
     * Finds the parent process activity for a subprocess task.
     * 查找子流程任务的父流程活动。
     */
    private String findParentProcessActivity(String taskId) {
        FindParentProcessActivityCmd command = new FindParentProcessActivityCmd(taskId);
        return commandExecutor.execute(command);
    }

    /**
     * Result class for step back validation.
     * 回退验证的结果类。
     */
    public static class StepBackValidationResult {
        private final boolean valid;
        private final String message;
        private final String messageZh;
        private final List<String> possibleTargets;

        public StepBackValidationResult(boolean valid, String message, String messageZh, List<String> possibleTargets) {
            this.valid = valid;
            this.message = message;
            this.messageZh = messageZh;
            this.possibleTargets = possibleTargets;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public String getMessageZh() {
            return messageZh;
        }

        public List<String> getPossibleTargets() {
            return possibleTargets;
        }
    }

    // Command class stubs - these would be implemented as separate command classes
    // 命令类存根 - 这些将作为单独的命令类实现

    private static class GetTaskEntityCmd implements org.flowable.common.engine.impl.interceptor.Command<org.flowable.task.service.impl.persistence.entity.TaskEntity> {
        private final String taskId;

        public GetTaskEntityCmd(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public org.flowable.task.service.impl.persistence.entity.TaskEntity execute(org.flowable.common.engine.impl.interceptor.CommandContext commandContext) {
            return org.flowable.engine.impl.util.CommandContextUtil.getTaskService(commandContext).getTask(taskId);
        }
    }

    private static class GetPossibleStepBackTargetsCmd implements org.flowable.common.engine.impl.interceptor.Command<List<String>> {
        private final String taskId;

        public GetPossibleStepBackTargetsCmd(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<String> execute(org.flowable.common.engine.impl.interceptor.CommandContext commandContext) {
            // Implementation would go here
            return java.util.Collections.emptyList();
        }
    }

    private static class ValidateStepBackCmd implements org.flowable.common.engine.impl.interceptor.Command<StepBackValidationResult> {
        private final String taskId;
        private final String targetActivityId;
        private final String userId;

        public ValidateStepBackCmd(String taskId, String targetActivityId, String userId) {
            this.taskId = taskId;
            this.targetActivityId = targetActivityId;
            this.userId = userId;
        }

        @Override
        public StepBackValidationResult execute(org.flowable.common.engine.impl.interceptor.CommandContext commandContext) {
            // Implementation would go here
            return new StepBackValidationResult(true, "Valid", "有效", java.util.Collections.emptyList());
        }
    }

    private static class FindRelatedMultiInstanceTasksCmd implements org.flowable.common.engine.impl.interceptor.Command<List<String>> {
        private final String taskId;

        public FindRelatedMultiInstanceTasksCmd(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<String> execute(org.flowable.common.engine.impl.interceptor.CommandContext commandContext) {
            // Implementation would go here
            return java.util.Collections.emptyList();
        }
    }

    private static class FindParentProcessActivityCmd implements org.flowable.common.engine.impl.interceptor.Command<String> {
        private final String taskId;

        public FindParentProcessActivityCmd(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public String execute(org.flowable.common.engine.impl.interceptor.CommandContext commandContext) {
            // Implementation would go here
            return null;
        }
    }
}