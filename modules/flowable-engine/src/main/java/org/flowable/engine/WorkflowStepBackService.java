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

import org.flowable.common.engine.api.FlowableException;

/**
 * Service providing workflow step back functionality with comprehensive validation.
 * All step back operations include parameter validation before execution.
 * 
 * @author Flowable
 */
public interface WorkflowStepBackService {

    /**
     * Steps back a workflow from current task to target task with validation.
     * This method validates all parameters before attempting the step back operation.
     * 
     * @param processInstanceId the ID of the process instance
     * @param currentTaskKey the key of the current task
     * @param targetTaskKey the key of the target task to step back to
     * @throws FlowableException if the step back operation fails
     * @throws org.flowable.common.engine.api.FlowableIllegalArgumentException if parameters are invalid
     */
    void stepBackWorkflow(String processInstanceId, String currentTaskKey, String targetTaskKey);

    /**
     * Validates step back parameters without performing the actual step back operation.
     * This method can be used to pre-validate parameters before calling stepBackWorkflow.
     * 
     * @param processInstanceId the ID of the process instance
     * @param currentTaskKey the key of the current task
     * @param targetTaskKey the key of the target task to step back to
     * @throws org.flowable.common.engine.api.FlowableIllegalArgumentException if parameters are invalid
     */
    void validateStepBackParameters(String processInstanceId, String currentTaskKey, String targetTaskKey);

    /**
     * Steps back a workflow with detailed validation including process definition checks.
     * This method performs the most comprehensive validation before step back operation.
     * 
     * @param processInstanceId the ID of the process instance
     * @param currentTaskKey the key of the current task
     * @param targetTaskKey the key of the target task to step back to
     * @param validateTaskKeys whether to validate that task keys exist in process definition
     * @throws FlowableException if the step back operation fails
     * @throws org.flowable.common.engine.api.FlowableIllegalArgumentException if parameters are invalid
     */
    void stepBackWorkflowWithValidation(String processInstanceId, String currentTaskKey, String targetTaskKey, boolean validateTaskKeys);
}