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
package org.flowable.engine;

import java.util.List;

import org.flowable.task.api.history.HistoricTaskInstance;

/**
 * Service for managing workflow step back operations and finding previous tasks.
 * 
 * This service provides functionality to navigate backwards through process flows
 * and find previously executed tasks in various BPMN scenarios. It supports:
 * 
 * <h3>Sequential Flow Navigation</h3>
 * <p>
 * In sequential flows, tasks are executed one after another in a linear fashion.
 * The step back operation identifies the immediate previous task by following
 * the incoming sequence flows backwards until a user task is found.
 * </p>
 * 
 * <h3>Parallel Gateway Scenarios</h3>
 * <p>
 * In parallel gateways, multiple execution paths can run concurrently. When
 * stepping back from a task that follows a parallel join gateway, this service
 * identifies all tasks that were executed in the parallel branches that
 * converged at the gateway.
 * </p>
 * 
 * <h3>Subprocess Navigation</h3>
 * <p>
 * For tasks within subprocesses (embedded subprocesses or call activities),
 * the service can find:
 * <ul>
 * <li>Previous tasks within the same subprocess</li>
 * <li>Tasks executed before the subprocess was entered</li>
 * <li>Tasks from parent processes (for call activities)</li>
 * </ul>
 * </p>
 * 
 * <h3>Validation and Error Handling</h3>
 * <p>
 * All operations include comprehensive validation to ensure:
 * <ul>
 * <li>Task exists and is accessible</li>
 * <li>Process definition is valid</li>
 * <li>User has appropriate permissions</li>
 * <li>Step back operation is allowed by process rules</li>
 * </ul>
 * </p>
 * 
 * @author Flowable Team
 */
public interface WorkflowStepBackService {

    /**
     * Gets the previous task in a sequential flow scenario.
     * 
     * <p>
     * This method is used when the current task is part of a simple sequential
     * flow where tasks are executed one after another. It navigates backwards
     * through the sequence flows to find the most recently completed user task.
     * </p>
     * 
     * <h4>Use Cases:</h4>
     * <ul>
     * <li>Linear approval workflows</li>
     * <li>Document review processes</li>
     * <li>Step-by-step data entry forms</li>
     * </ul>
     * 
     * <h4>Behavior:</h4>
     * <ul>
     * <li>Follows incoming sequence flows backwards</li>
     * <li>Skips non-user tasks (service tasks, gateways, etc.)</li>
     * <li>Returns the first user task found</li>
     * <li>Returns null if no previous user task exists</li>
     * </ul>
     * 
     * @param currentTaskId the ID of the current task from which to step back
     * @return the previous task in the sequential flow, or null if none exists
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException if the current task is not found
     * @throws org.flowable.common.engine.api.FlowableException if an error occurs during navigation
     */
    HistoricTaskInstance getPreviousTaskInSequentialFlow(String currentTaskId);

    /**
     * Gets previous tasks in parallel gateway scenarios.
     * 
     * <p>
     * This method is used when the current task follows a parallel join gateway
     * and you need to identify all tasks that were executed in the parallel
     * branches before the convergence. This is essential for understanding
     * the complete context of parallel execution paths.
     * </p>
     * 
     * <h4>Use Cases:</h4>
     * <ul>
     * <li>Multi-party approval processes</li>
     * <li>Parallel document reviews</li>
     * <li>Concurrent data validation tasks</li>
     * </ul>
     * 
     * <h4>Behavior:</h4>
     * <ul>
     * <li>Identifies parallel join gateways in incoming flows</li>
     * <li>Traverses all branches that converge at the gateway</li>
     * <li>Collects all user tasks from parallel branches</li>
     * <li>Returns tasks ordered by completion time (most recent first)</li>
     * </ul>
     * 
     * @param currentTaskId the ID of the current task from which to step back
     * @return list of previous tasks from parallel branches, empty list if none exist
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException if the current task is not found
     * @throws org.flowable.common.engine.api.FlowableException if an error occurs during navigation
     */
    List<HistoricTaskInstance> getPreviousTasksInParallelGateway(String currentTaskId);

    /**
     * Gets previous tasks in subprocess scenarios.
     * 
     * <p>
     * This method handles complex subprocess hierarchies including embedded
     * subprocesses and call activities. It provides comprehensive navigation
     * through the subprocess boundaries to find all relevant previous tasks.
     * </p>
     * 
     * <h4>Use Cases:</h4>
     * <ul>
     * <li>Nested business processes</li>
     * <li>Reusable subprocess components</li>
     * <li>Complex workflow hierarchies</li>
     * </ul>
     * 
     * <h4>Behavior:</h4>
     * <ul>
     * <li>Identifies the containing subprocess</li>
     * <li>Finds tasks within the same subprocess scope</li>
     * <li>Navigates to tasks before subprocess entry</li>
     * <li>Handles call activity relationships</li>
     * </ul>
     * 
     * @param currentTaskId the ID of the current task from which to step back
     * @return list of previous tasks from subprocess hierarchy, empty list if none exist
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException if the current task is not found
     * @throws org.flowable.common.engine.api.FlowableException if an error occurs during navigation
     */
    List<HistoricTaskInstance> getPreviousTasksInSubprocess(String currentTaskId);

    /**
     * Gets all previous tasks using automatic detection of flow scenario.
     * 
     * <p>
     * This is a convenience method that automatically detects the type of
     * flow scenario (sequential, parallel, or subprocess) and applies the
     * appropriate navigation strategy. It provides a unified interface
     * when the specific flow type is not known in advance.
     * </p>
     * 
     * <h4>Detection Logic:</h4>
     * <ul>
     * <li>Analyzes the current task's position in the process</li>
     * <li>Identifies incoming flow patterns</li>
     * <li>Detects subprocess boundaries</li>
     * <li>Applies the most appropriate navigation method</li>
     * </ul>
     * 
     * @param currentTaskId the ID of the current task from which to step back
     * @return list of all relevant previous tasks, empty list if none exist
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException if the current task is not found
     * @throws org.flowable.common.engine.api.FlowableException if an error occurs during navigation
     */
    List<HistoricTaskInstance> getAllPreviousTasks(String currentTaskId);

    /**
     * Validates if step back operation is allowed for the given task.
     * 
     * <p>
     * This method performs comprehensive validation to determine if a step
     * back operation can be safely performed. It checks process state,
     * business rules, and security constraints.
     * </p>
     * 
     * <h4>Validation Checks:</h4>
     * <ul>
     * <li>Task exists and is accessible</li>
     * <li>Process instance is in a valid state</li>
     * <li>User has required permissions</li>
     * <li>No blocking conditions exist</li>
     * <li>Process definition allows step back</li>
     * </ul>
     * 
     * @param currentTaskId the ID of the task to validate
     * @return true if step back is allowed, false otherwise
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException if the current task is not found
     */
    boolean isStepBackAllowed(String currentTaskId);

    /**
     * Validates if step back operation is allowed with detailed error information.
     * 
     * <p>
     * Similar to {@link #isStepBackAllowed(String)} but provides detailed
     * information about validation failures. This is useful for providing
     * meaningful error messages to users.
     * </p>
     * 
     * @param currentTaskId the ID of the task to validate
     * @return validation result with detailed information
     * @throws org.flowable.common.engine.api.FlowableObjectNotFoundException if the current task is not found
     */
    StepBackValidationResult validateStepBack(String currentTaskId);

    /**
     * Result of step back validation containing detailed information.
     */
    interface StepBackValidationResult {
        
        /**
         * @return true if step back is allowed
         */
        boolean isAllowed();
        
        /**
         * @return list of validation error messages, empty if validation passed
         */
        List<String> getValidationErrors();
        
        /**
         * @return list of warning messages that don't prevent step back but should be noted
         */
        List<String> getWarnings();
        
        /**
         * @return the scenario type detected (SEQUENTIAL, PARALLEL, SUBPROCESS, or UNKNOWN)
         */
        StepBackScenario getScenario();
    }

    /**
     * Enumeration of step back scenarios.
     */
    enum StepBackScenario {
        /** Simple sequential flow with tasks executed one after another */
        SEQUENTIAL,
        
        /** Parallel gateway scenario with concurrent execution paths */
        PARALLEL,
        
        /** Subprocess scenario with embedded or call activity subprocesses */
        SUBPROCESS,
        
        /** Complex scenario with mixed flow types */
        MIXED,
        
        /** Scenario could not be determined */
        UNKNOWN
    }
}