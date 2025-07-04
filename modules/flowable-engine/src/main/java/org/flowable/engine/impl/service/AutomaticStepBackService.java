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
package org.flowable.engine.impl.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.PreviousTaskFinder;
import org.flowable.task.api.Task;
import org.flowable.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that provides automatic step back functionality by combining
 * PreviousTaskFinder with WorkflowStepBackService to automatically determine
 * and execute step back operations without requiring explicit target task specification.
 * 
 * This service handles all workflow scenarios including parallel gateways and subprocesses,
 * and provides comprehensive error handling and logging.
 * 
 * @author Flowable Team
 */
public class AutomaticStepBackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticStepBackService.class);

    private final WorkflowStepBackService workflowStepBackService;

    public AutomaticStepBackService() {
        this.workflowStepBackService = new WorkflowStepBackService();
    }

    public AutomaticStepBackService(WorkflowStepBackService workflowStepBackService) {
        this.workflowStepBackService = workflowStepBackService;
    }

    /**
     * Automatically performs a step back operation from the current task to the most appropriate previous task.
     * This method automatically determines the target task without requiring explicit specification.
     * 
     * @param currentTaskId the current task to step back from
     * @param processInstanceId the process instance ID
     * @return the ID of the task that was stepped back to
     */
    public String performAutomaticStepBack(String currentTaskId, String processInstanceId) {
        return performAutomaticStepBack(currentTaskId, processInstanceId, null);
    }

    /**
     * Automatically performs a step back operation from the current task to the most appropriate previous task.
     * This method automatically determines the target task without requiring explicit specification.
     * 
     * @param currentTaskId the current task to step back from
     * @param processInstanceId the process instance ID
     * @param variables optional variables to set during step back
     * @return the ID of the task that was stepped back to
     */
    public String performAutomaticStepBack(String currentTaskId, String processInstanceId, Map<String, Object> variables) {
        if (currentTaskId == null || processInstanceId == null) {
            throw new FlowableException("Current task ID and process instance ID are required");
        }

        try {
            LOGGER.info("Starting automatic step back for task {} in process instance {}", currentTaskId, processInstanceId);

            // Get the execution entity
            CommandContext commandContext = CommandContextUtil.getCommandContext();
            ExecutionEntity processInstanceExecution = CommandContextUtil.getExecutionEntityManager(commandContext)
                    .findById(processInstanceId);

            if (processInstanceExecution == null) {
                throw new FlowableException("Process instance not found: " + processInstanceId);
            }

            // Automatically determine the target task
            String targetTaskId = determineTargetTask(currentTaskId, processInstanceExecution);
            
            if (targetTaskId == null) {
                throw new FlowableException("No suitable previous task found for step back from task: " + currentTaskId);
            }

            LOGGER.info("Automatically determined target task {} for step back from {}", targetTaskId, currentTaskId);

            // Validate the step back type and handle accordingly
            StepBackType stepBackType = determineStepBackType(currentTaskId, targetTaskId, processInstanceExecution);
            LOGGER.debug("Determined step back type: {}", stepBackType);

            // Execute the step back operation
            workflowStepBackService.executeStepBack(currentTaskId, targetTaskId, processInstanceId, variables);

            LOGGER.info("Successfully completed automatic step back from {} to {}", currentTaskId, targetTaskId);
            return targetTaskId;

        } catch (Exception e) {
            LOGGER.error("Error performing automatic step back for task {}: {}", currentTaskId, e.getMessage(), e);
            throw new FlowableException("Automatic step back operation failed", e);
        }
    }

    /**
     * Automatically determines the most appropriate target task for step back.
     * This method uses PreviousTaskFinder to identify previous tasks and applies
     * logic to select the best candidate.
     */
    private String determineTargetTask(String currentTaskId, ExecutionEntity executionEntity) {
        try {
            // Find all previous tasks
            List<String> previousTasks = PreviousTaskFinder.findPreviousTasks(currentTaskId, executionEntity);
            
            if (previousTasks.isEmpty()) {
                LOGGER.warn("No previous tasks found for current task: {}", currentTaskId);
                return null;
            }

            // If only one previous task, use it
            if (previousTasks.size() == 1) {
                String targetTask = previousTasks.get(0);
                LOGGER.debug("Single previous task found: {}", targetTask);
                return targetTask;
            }

            // Multiple previous tasks - determine the most appropriate one
            String targetTask = selectBestTargetTask(previousTasks, currentTaskId, executionEntity);
            LOGGER.debug("Selected best target task {} from {} candidates", targetTask, previousTasks.size());
            
            return targetTask;

        } catch (Exception e) {
            LOGGER.error("Error determining target task for {}: {}", currentTaskId, e.getMessage(), e);
            throw new FlowableException("Failed to determine target task", e);
        }
    }

    /**
     * Selects the best target task from multiple previous task candidates.
     * This method implements logic to choose the most logical step back target.
     */
    private String selectBestTargetTask(List<String> previousTasks, String currentTaskId, ExecutionEntity executionEntity) {
        // For now, use the PreviousTaskFinder's logic to find the most recent task
        // In a more sophisticated implementation, this could analyze:
        // - Execution history to find the most recently completed task
        // - Business rules to prefer certain task types
        // - User preferences or configuration
        
        String mostRecentTask = PreviousTaskFinder.findMostRecentPreviousTask(previousTasks, executionEntity);
        
        if (mostRecentTask != null) {
            LOGGER.debug("Selected most recent previous task: {}", mostRecentTask);
            return mostRecentTask;
        }
        
        // Fallback to first task if no specific logic applies
        LOGGER.debug("Using first previous task as fallback: {}", previousTasks.get(0));
        return previousTasks.get(0);
    }

    /**
     * Determines the type of step back operation based on workflow analysis.
     */
    private StepBackType determineStepBackType(String currentTaskId, String targetTaskId, ExecutionEntity executionEntity) {
        // This could be enhanced to provide more detailed type information
        // For now, we rely on the WorkflowStepBackService to determine the strategy
        
        try {
            // Basic validation that this is a valid step back
            List<String> previousTasks = PreviousTaskFinder.findPreviousTasks(currentTaskId, executionEntity);
            
            if (!previousTasks.contains(targetTaskId)) {
                return StepBackType.INVALID;
            }
            
            if (previousTasks.size() == 1) {
                return StepBackType.SIMPLE;
            } else {
                return StepBackType.COMPLEX;
            }
            
        } catch (Exception e) {
            LOGGER.warn("Error determining step back type, defaulting to UNKNOWN: {}", e.getMessage());
            return StepBackType.UNKNOWN;
        }
    }

    /**
     * Validates whether an automatic step back is possible for the given task.
     * 
     * @param currentTaskId the current task ID
     * @param processInstanceId the process instance ID
     * @return true if automatic step back is possible, false otherwise
     */
    public boolean canPerformAutomaticStepBack(String currentTaskId, String processInstanceId) {
        if (currentTaskId == null || processInstanceId == null) {
            return false;
        }

        try {
            CommandContext commandContext = CommandContextUtil.getCommandContext();
            ExecutionEntity processInstanceExecution = CommandContextUtil.getExecutionEntityManager(commandContext)
                    .findById(processInstanceId);

            if (processInstanceExecution == null) {
                return false;
            }

            // Check if there are any previous tasks
            List<String> previousTasks = PreviousTaskFinder.findPreviousTasks(currentTaskId, processInstanceExecution);
            boolean canStepBack = !previousTasks.isEmpty();
            
            LOGGER.debug("Can perform automatic step back for task {}: {} (found {} previous tasks)", 
                    currentTaskId, canStepBack, previousTasks.size());
            
            return canStepBack;

        } catch (Exception e) {
            LOGGER.warn("Error checking if automatic step back is possible for task {}: {}", 
                    currentTaskId, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the list of possible previous tasks for step back.
     * This method can be used to provide users with options for manual step back.
     * 
     * @param currentTaskId the current task ID
     * @param processInstanceId the process instance ID
     * @return list of previous task IDs that can be used for step back
     */
    public List<String> getPossibleStepBackTargets(String currentTaskId, String processInstanceId) {
        if (currentTaskId == null || processInstanceId == null) {
            throw new FlowableException("Current task ID and process instance ID are required");
        }

        try {
            CommandContext commandContext = CommandContextUtil.getCommandContext();
            ExecutionEntity processInstanceExecution = CommandContextUtil.getExecutionEntityManager(commandContext)
                    .findById(processInstanceId);

            if (processInstanceExecution == null) {
                throw new FlowableException("Process instance not found: " + processInstanceId);
            }

            List<String> previousTasks = PreviousTaskFinder.findPreviousTasks(currentTaskId, processInstanceExecution);
            LOGGER.debug("Found {} possible step back targets for task {}: {}", 
                    previousTasks.size(), currentTaskId, previousTasks);
            
            return previousTasks;

        } catch (Exception e) {
            LOGGER.error("Error getting possible step back targets for task {}: {}", 
                    currentTaskId, e.getMessage(), e);
            throw new FlowableException("Failed to get step back targets", e);
        }
    }

    /**
     * Enumeration of step back types for classification purposes.
     */
    public enum StepBackType {
        SIMPLE,     // Single previous task, straightforward step back
        COMPLEX,    // Multiple previous tasks, requires selection logic
        INVALID,    // Invalid step back request
        UNKNOWN     // Type could not be determined
    }
}