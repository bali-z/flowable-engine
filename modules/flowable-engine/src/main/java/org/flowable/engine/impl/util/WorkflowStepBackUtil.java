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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

/**
 * Utility class for handling workflow step back functionality in Flowable.
 * 
 * This utility provides thread-safe methods to handle different scenarios of workflow step back:
 * - Simple sequential flow step back
 * - Step back in parallel gateway branches  
 * - Step back from a parallel gateway branch to a previous node
 * - Step back between main process and sub-processes
 * 
 * @author Flowable Team
 */
public class WorkflowStepBackUtil {

    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Process type enumeration for different process patterns
     */
    public enum ProcessType {
        SEQUENTIAL,
        PARALLEL_GATEWAY,
        SUBPROCESS,
        EVENT_SUBPROCESS
    }

    /**
     * Step back result information
     */
    public static class StepBackResult {
        private final boolean success;
        private final String message;
        private final List<String> movedExecutionIds;
        private final String targetActivityId;

        public StepBackResult(boolean success, String message, List<String> movedExecutionIds, String targetActivityId) {
            this.success = success;
            this.message = message;
            this.movedExecutionIds = movedExecutionIds != null ? new ArrayList<>(movedExecutionIds) : new ArrayList<>();
            this.targetActivityId = targetActivityId;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getMovedExecutionIds() {
            return Collections.unmodifiableList(movedExecutionIds);
        }

        public String getTargetActivityId() {
            return targetActivityId;
        }
    }

    /**
     * Gets the previous task in sequential flow for the given task.
     * 
     * @param taskId the current task id
     * @param processEngineConfiguration the process engine configuration
     * @return the previous task information, or null if no previous task exists
     */
    public static HistoricTaskInstance getPreviousTaskInSequentialFlow(String taskId, ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(taskId)) {
            throw new FlowableIllegalArgumentException("Task ID cannot be null or empty");
        }

        lock.readLock().lock();
        try {
            TaskService taskService = processEngineConfiguration.getTaskService();
            HistoryService historyService = processEngineConfiguration.getHistoryService();

            // Get current task
            Task currentTask = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (currentTask == null) {
                // Try historic task
                HistoricTaskInstance currentHistoricTask = historyService.createHistoricTaskInstanceQuery()
                    .taskId(taskId).singleResult();
                if (currentHistoricTask == null) {
                    throw new FlowableException("Task with id '" + taskId + "' not found");
                }
                
                // Get previous task in same process instance
                List<HistoricTaskInstance> previousTasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(currentHistoricTask.getProcessInstanceId())
                    .finished()
                    .taskCreatedBefore(currentHistoricTask.getCreateTime())
                    .orderByHistoricTaskInstanceEndTime().desc()
                    .list();
                
                return previousTasks.isEmpty() ? null : previousTasks.get(0);
            }

            // Get previous task in same process instance
            List<HistoricTaskInstance> previousTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(currentTask.getProcessInstanceId())
                .finished()
                .taskCreatedBefore(currentTask.getCreateTime())
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
            
            return previousTasks.isEmpty() ? null : previousTasks.get(0);
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Handles step back in parallel gateway scenarios.
     * 
     * @param taskId the current task id
     * @param targetActivityId the target activity to step back to
     * @param processEngineConfiguration the process engine configuration
     * @return step back result
     */
    public static StepBackResult handleParallelGatewayStepBack(String taskId, String targetActivityId, 
                                                              ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(targetActivityId)) {
            throw new FlowableIllegalArgumentException("Task ID and target activity ID cannot be null or empty");
        }

        lock.writeLock().lock();
        try {
            TaskService taskService = processEngineConfiguration.getTaskService();
            RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();

            // Get current task
            Task currentTask = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (currentTask == null) {
                return new StepBackResult(false, "Task with id '" + taskId + "' not found", null, targetActivityId);
            }

            // Validate target is reachable backwards
            if (!isValidStepBackTarget(currentTask.getProcessInstanceId(), currentTask.getTaskDefinitionKey(), 
                                     targetActivityId, processEngineConfiguration)) {
                return new StepBackResult(false, "Target activity '" + targetActivityId + "' is not a valid step back target", null, targetActivityId);
            }

            // Find parallel branch executions
            List<Execution> parallelExecutions = findParallelBranchExecutions(currentTask.getProcessInstanceId(), runtimeService);
            
            List<String> movedExecutionIds = new ArrayList<>();
            
            // Move all parallel executions to target activity
            for (Execution execution : parallelExecutions) {
                try {
                    runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(currentTask.getProcessInstanceId())
                        .moveExecutionToActivityId(execution.getId(), targetActivityId)
                        .changeState();
                    movedExecutionIds.add(execution.getId());
                } catch (Exception e) {
                    return new StepBackResult(false, "Failed to move execution " + execution.getId() + ": " + e.getMessage(), 
                                            movedExecutionIds, targetActivityId);
                }
            }

            return new StepBackResult(true, "Successfully stepped back to " + targetActivityId, movedExecutionIds, targetActivityId);
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Handles step back to parallel gateway branches.
     * 
     * @param taskId the current task id
     * @param targetBranchActivityIds the target activities in different branches
     * @param processEngineConfiguration the process engine configuration
     * @return step back result
     */
    public static StepBackResult handleStepBackToParallelGatewayBranches(String taskId, List<String> targetBranchActivityIds,
                                                                        ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(taskId) || targetBranchActivityIds == null || targetBranchActivityIds.isEmpty()) {
            throw new FlowableIllegalArgumentException("Task ID and target branch activity IDs cannot be null or empty");
        }

        lock.writeLock().lock();
        try {
            TaskService taskService = processEngineConfiguration.getTaskService();
            RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();

            // Get current task
            Task currentTask = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (currentTask == null) {
                return new StepBackResult(false, "Task with id '" + taskId + "' not found", null, null);
            }

            List<String> movedExecutionIds = new ArrayList<>();
            
            // Create executions for each target branch
            for (int i = 0; i < targetBranchActivityIds.size(); i++) {
                String targetActivityId = targetBranchActivityIds.get(i);
                
                if (!isValidStepBackTarget(currentTask.getProcessInstanceId(), currentTask.getTaskDefinitionKey(), 
                                         targetActivityId, processEngineConfiguration)) {
                    return new StepBackResult(false, "Target activity '" + targetActivityId + "' is not a valid step back target", 
                                            movedExecutionIds, null);
                }

                try {
                    if (i == 0) {
                        // Move current execution to first target
                        runtimeService.createChangeActivityStateBuilder()
                            .processInstanceId(currentTask.getProcessInstanceId())
                            .moveExecutionToActivityId(currentTask.getExecutionId(), targetActivityId)
                            .changeState();
                        movedExecutionIds.add(currentTask.getExecutionId());
                    } else {
                        // Create new executions for additional branches using change activity state builder
                        runtimeService.createChangeActivityStateBuilder()
                            .processInstanceId(currentTask.getProcessInstanceId())
                            .moveExecutionToActivityId(currentTask.getExecutionId(), targetActivityId)
                            .changeState();
                        movedExecutionIds.add(currentTask.getExecutionId());
                    }
                } catch (Exception e) {
                    return new StepBackResult(false, "Failed to create branch execution for activity " + targetActivityId + ": " + e.getMessage(), 
                                            movedExecutionIds, null);
                }
            }

            return new StepBackResult(true, "Successfully stepped back to parallel branches", movedExecutionIds, null);
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Handles step back between main process and sub-processes.
     * 
     * @param taskId the current task id
     * @param targetActivityId the target activity id
     * @param processEngineConfiguration the process engine configuration
     * @return step back result
     */
    public static StepBackResult handleSubProcessStepBack(String taskId, String targetActivityId,
                                                         ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(taskId) || StringUtils.isEmpty(targetActivityId)) {
            throw new FlowableIllegalArgumentException("Task ID and target activity ID cannot be null or empty");
        }

        lock.writeLock().lock();
        try {
            TaskService taskService = processEngineConfiguration.getTaskService();
            RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();

            // Get current task
            Task currentTask = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (currentTask == null) {
                return new StepBackResult(false, "Task with id '" + taskId + "' not found", null, targetActivityId);
            }

            // Determine if stepping back from subprocess to main process or vice versa
            ProcessType currentProcessType = determineProcessType(currentTask.getProcessInstanceId(), 
                                                                currentTask.getTaskDefinitionKey(), processEngineConfiguration);
            ProcessType targetProcessType = determineProcessType(currentTask.getProcessInstanceId(), 
                                                               targetActivityId, processEngineConfiguration);

            if (!isValidCrossProcessStepBack(currentProcessType, targetProcessType)) {
                return new StepBackResult(false, "Invalid cross-process step back from " + currentProcessType + " to " + targetProcessType, 
                                        null, targetActivityId);
            }

            List<String> movedExecutionIds = new ArrayList<>();
            
            try {
                // Handle subprocess step back
                if (currentProcessType == ProcessType.SUBPROCESS || targetProcessType == ProcessType.SUBPROCESS) {
                    // Get subprocess executions
                    List<Execution> subProcessExecutions = runtimeService.createExecutionQuery()
                        .processInstanceId(currentTask.getProcessInstanceId())
                        .activityId(currentTask.getTaskDefinitionKey())
                        .list();
                    
                    for (Execution execution : subProcessExecutions) {
                        runtimeService.createChangeActivityStateBuilder()
                            .processInstanceId(currentTask.getProcessInstanceId())
                            .moveExecutionToActivityId(execution.getId(), targetActivityId)
                            .changeState();
                        movedExecutionIds.add(execution.getId());
                    }
                } else {
                    // Regular step back
                    runtimeService.createChangeActivityStateBuilder()
                        .processInstanceId(currentTask.getProcessInstanceId())
                        .moveExecutionToActivityId(currentTask.getExecutionId(), targetActivityId)
                        .changeState();
                    movedExecutionIds.add(currentTask.getExecutionId());
                }

                return new StepBackResult(true, "Successfully stepped back from " + currentProcessType + " to " + targetProcessType, 
                                        movedExecutionIds, targetActivityId);
                
            } catch (Exception e) {
                return new StepBackResult(false, "Failed to step back: " + e.getMessage(), movedExecutionIds, targetActivityId);
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Determines the process type of the given activity.
     * 
     * @param processInstanceId the process instance id
     * @param activityId the activity id
     * @param processEngineConfiguration the process engine configuration
     * @return the process type
     */
    public static ProcessType determineProcessType(String processInstanceId, String activityId, 
                                                  ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(processInstanceId) || StringUtils.isEmpty(activityId)) {
            throw new FlowableIllegalArgumentException("Process instance ID and activity ID cannot be null or empty");
        }

        lock.readLock().lock();
        try {
            // Execute within a command context to ensure proper access to process definition
            return processEngineConfiguration.getCommandExecutor().execute(commandContext -> {
                RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
                
                // Get process definition through process instance
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
                
                if (processInstance == null) {
                    throw new FlowableException("Process instance with id '" + processInstanceId + "' not found");
                }

                Process process = ProcessDefinitionUtil.getProcess(processInstance.getProcessDefinitionId());
                FlowElement flowElement = process.getFlowElement(activityId, true);
                
                if (flowElement == null) {
                    throw new FlowableException("Activity with id '" + activityId + "' not found in process");
                }

                // Check if activity is in subprocess
                if (process.findParent(flowElement) instanceof SubProcess) {
                    return ProcessType.SUBPROCESS;
                }

                // Check if activity has parallel gateway connections
                if (flowElement instanceof FlowNode) {
                    FlowNode flowNode = (FlowNode) flowElement;
                    if (hasParallelGatewayConnections(flowNode)) {
                        return ProcessType.PARALLEL_GATEWAY;
                    }
                }

                return ProcessType.SEQUENTIAL;
            });
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Validates that the target activity is a valid step back target.
     * 
     * @param processInstanceId the process instance id
     * @param currentActivityId the current activity id  
     * @param targetActivityId the target activity id
     * @param processEngineConfiguration the process engine configuration
     * @return true if valid step back target, false otherwise
     */
    public static boolean isValidStepBackTarget(String processInstanceId, String currentActivityId, String targetActivityId,
                                               ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(processInstanceId) || StringUtils.isEmpty(currentActivityId) || StringUtils.isEmpty(targetActivityId)) {
            return false;
        }

        lock.readLock().lock();
        try {
            HistoryService historyService = processEngineConfiguration.getHistoryService();
            
            // Check if target activity was executed before current activity in this process instance
            List<HistoricActivityInstance> targetActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(targetActivityId)
                .finished()
                .orderByHistoricActivityInstanceEndTime().desc()
                .list();
                
            List<HistoricActivityInstance> currentActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(currentActivityId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
            
            if (targetActivities.isEmpty() || currentActivities.isEmpty()) {
                return false;
            }
            
            // Ensure target activity was executed before current activity
            return targetActivities.get(0).getEndTime().before(currentActivities.get(0).getStartTime());
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets necessary task/execution information for step back operations.
     * 
     * @param taskId the task id
     * @param processEngineConfiguration the process engine configuration
     * @return map containing task and execution information
     */
    public static Map<String, Object> getTaskExecutionInfo(String taskId, ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (StringUtils.isEmpty(taskId)) {
            throw new FlowableIllegalArgumentException("Task ID cannot be null or empty");
        }

        lock.readLock().lock();
        try {
            TaskService taskService = processEngineConfiguration.getTaskService();
            RuntimeService runtimeService = processEngineConfiguration.getRuntimeService();
            
            Map<String, Object> info = new HashMap<>();
            
            // Get current task
            Task currentTask = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (currentTask == null) {
                return Collections.emptyMap();
            }
            
            info.put("task", currentTask);
            info.put("processInstanceId", currentTask.getProcessInstanceId());
            info.put("executionId", currentTask.getExecutionId());
            info.put("taskDefinitionKey", currentTask.getTaskDefinitionKey());
            
            // Get execution information
            Execution execution = runtimeService.createExecutionQuery()
                .executionId(currentTask.getExecutionId())
                .singleResult();
            info.put("execution", execution);
            
            // Get process type
            ProcessType processType = processEngineConfiguration.getCommandExecutor().execute(commandContext -> {
                return determineProcessType(currentTask.getProcessInstanceId(), 
                                          currentTask.getTaskDefinitionKey(), processEngineConfiguration);
            });
            info.put("processType", processType);
            
            return info;
            
        } finally {
            lock.readLock().unlock();
        }
    }

    // Private helper methods

    private static List<Execution> findParallelBranchExecutions(String processInstanceId, RuntimeService runtimeService) {
        return runtimeService.createExecutionQuery()
            .processInstanceId(processInstanceId)
            .onlyChildExecutions()
            .list();
    }

    private static boolean hasParallelGatewayConnections(FlowNode flowNode) {
        // Check incoming flows for parallel gateway
        for (SequenceFlow incomingFlow : flowNode.getIncomingFlows()) {
            if (incomingFlow.getSourceFlowElement() instanceof ParallelGateway) {
                return true;
            }
        }
        
        // Check outgoing flows for parallel gateway
        for (SequenceFlow outgoingFlow : flowNode.getOutgoingFlows()) {
            if (outgoingFlow.getTargetFlowElement() instanceof ParallelGateway) {
                return true;
            }
        }
        
        return false;
    }

    private static boolean isValidCrossProcessStepBack(ProcessType currentType, ProcessType targetType) {
        // Allow step back between main process and subprocess
        return (currentType == ProcessType.SUBPROCESS && targetType == ProcessType.SEQUENTIAL) ||
               (currentType == ProcessType.SEQUENTIAL && targetType == ProcessType.SUBPROCESS) ||
               (currentType == targetType);
    }
}