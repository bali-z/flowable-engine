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

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.runtime.ChangeActivityStateBuilderImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.engine.impl.util.PreviousTaskFinder;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for handling workflow step back operations.
 * This service provides the core functionality for moving workflow execution
 * back to previous tasks, handling various workflow patterns including
 * parallel gateways and subprocesses.
 * 
 * @author Flowable Team
 */
public class WorkflowStepBackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackService.class);

    /**
     * Executes a step back operation from the current task to a target task.
     * 
     * @param currentTaskId the current task to step back from
     * @param targetTaskId the target task to step back to
     * @param processInstanceId the process instance ID
     * @param variables optional variables to set during step back
     */
    public void executeStepBack(String currentTaskId, String targetTaskId, String processInstanceId, 
            Map<String, Object> variables) {
        
        if (currentTaskId == null || targetTaskId == null || processInstanceId == null) {
            throw new FlowableException("Current task ID, target task ID, and process instance ID are required");
        }

        try {
            LOGGER.info("Executing step back from task {} to task {} in process instance {}", 
                    currentTaskId, targetTaskId, processInstanceId);

            CommandContext commandContext = CommandContextUtil.getCommandContext();
            ExecutionEntity processInstanceExecution = CommandContextUtil.getExecutionEntityManager(commandContext)
                    .findById(processInstanceId);

            if (processInstanceExecution == null) {
                throw new FlowableException("Process instance not found: " + processInstanceId);
            }

            // Validate the step back operation
            validateStepBackOperation(currentTaskId, targetTaskId, processInstanceExecution);

            // Determine step back strategy based on workflow structure
            StepBackStrategy strategy = determineStepBackStrategy(currentTaskId, targetTaskId, processInstanceExecution);
            LOGGER.debug("Determined step back strategy: {}", strategy);

            // Execute the step back based on strategy
            executeStepBackWithStrategy(strategy, currentTaskId, targetTaskId, processInstanceExecution, variables);

            LOGGER.info("Successfully completed step back operation from {} to {}", currentTaskId, targetTaskId);

        } catch (Exception e) {
            LOGGER.error("Error executing step back from task {} to task {}: {}", 
                    currentTaskId, targetTaskId, e.getMessage(), e);
            throw new FlowableException("Step back operation failed", e);
        }
    }

    /**
     * Validates that the step back operation is valid and safe to execute.
     */
    private void validateStepBackOperation(String currentTaskId, String targetTaskId, ExecutionEntity processInstanceExecution) {
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceExecution.getProcessDefinitionId());
        
        // Validate current task exists
        FlowElement currentElement = bpmnModel.getFlowElement(currentTaskId);
        if (currentElement == null || !(currentElement instanceof UserTask)) {
            throw new FlowableException("Current task not found or not a user task: " + currentTaskId);
        }

        // Validate target task exists
        FlowElement targetElement = bpmnModel.getFlowElement(targetTaskId);
        if (targetElement == null || !(targetElement instanceof UserTask)) {
            throw new FlowableException("Target task not found or not a user task: " + targetTaskId);
        }

        // Validate that target task is actually a previous task
        List<String> previousTasks = PreviousTaskFinder.findPreviousTasks(currentTaskId, processInstanceExecution);
        if (!previousTasks.contains(targetTaskId)) {
            throw new FlowableException("Target task " + targetTaskId + " is not a valid previous task for " + currentTaskId);
        }

        LOGGER.debug("Step back validation passed for {} -> {}", currentTaskId, targetTaskId);
    }

    /**
     * Determines the appropriate step back strategy based on the workflow structure.
     */
    private StepBackStrategy determineStepBackStrategy(String currentTaskId, String targetTaskId, 
            ExecutionEntity processInstanceExecution) {
        
        BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(processInstanceExecution.getProcessDefinitionId());
        FlowElement targetElement = bpmnModel.getFlowElement(targetTaskId);
        
        // Check if target task is in a parallel gateway scenario
        if (isInParallelGatewayScenario(targetElement, bpmnModel)) {
            return StepBackStrategy.PARALLEL_GATEWAY;
        }
        
        // Check if target task is in a subprocess
        if (isInSubProcessScenario(targetElement, bpmnModel)) {
            return StepBackStrategy.SUBPROCESS;
        }
        
        // Default to simple step back
        return StepBackStrategy.SIMPLE;
    }

    /**
     * Checks if the target element is involved in a parallel gateway scenario.
     */
    private boolean isInParallelGatewayScenario(FlowElement targetElement, BpmnModel bpmnModel) {
        // Check outgoing flows for parallel gateways
        if (targetElement instanceof UserTask) {
            UserTask userTask = (UserTask) targetElement;
            return userTask.getOutgoingFlows().stream()
                    .anyMatch(flow -> {
                        FlowElement targetFlow = bpmnModel.getFlowElement(flow.getTargetRef());
                        return targetFlow instanceof ParallelGateway;
                    });
        }
        return false;
    }

    /**
     * Checks if the target element is within a subprocess.
     */
    private boolean isInSubProcessScenario(FlowElement targetElement, BpmnModel bpmnModel) {
        return targetElement.getParentContainer() instanceof SubProcess;
    }

    /**
     * Executes the step back operation using the determined strategy.
     */
    private void executeStepBackWithStrategy(StepBackStrategy strategy, String currentTaskId, String targetTaskId, 
            ExecutionEntity processInstanceExecution, Map<String, Object> variables) {
        
        switch (strategy) {
            case PARALLEL_GATEWAY:
                executeParallelGatewayStepBack(currentTaskId, targetTaskId, processInstanceExecution, variables);
                break;
            case SUBPROCESS:
                executeSubProcessStepBack(currentTaskId, targetTaskId, processInstanceExecution, variables);
                break;
            case SIMPLE:
            default:
                executeSimpleStepBack(currentTaskId, targetTaskId, processInstanceExecution, variables);
                break;
        }
    }

    /**
     * Executes a simple step back operation.
     */
    private void executeSimpleStepBack(String currentTaskId, String targetTaskId, 
            ExecutionEntity processInstanceExecution, Map<String, Object> variables) {
        
        LOGGER.debug("Executing simple step back from {} to {}", currentTaskId, targetTaskId);
        
        ChangeActivityStateBuilder changeBuilder = new ChangeActivityStateBuilderImpl()
                .processInstanceId(processInstanceExecution.getId())
                .moveActivityIdTo(currentTaskId, targetTaskId);
        
        if (variables != null && !variables.isEmpty()) {
            changeBuilder.processVariables(variables);
        }
        
        changeBuilder.changeState();
    }

    /**
     * Executes a step back operation in a parallel gateway scenario.
     */
    private void executeParallelGatewayStepBack(String currentTaskId, String targetTaskId, 
            ExecutionEntity processInstanceExecution, Map<String, Object> variables) {
        
        LOGGER.debug("Executing parallel gateway step back from {} to {}", currentTaskId, targetTaskId);
        
        // In parallel gateway scenarios, we need to handle multiple execution paths
        // This is a simplified implementation - in practice, this might need more sophisticated logic
        ChangeActivityStateBuilder changeBuilder = new ChangeActivityStateBuilderImpl()
                .processInstanceId(processInstanceExecution.getId())
                .moveActivityIdTo(currentTaskId, targetTaskId);
        
        if (variables != null && !variables.isEmpty()) {
            changeBuilder.processVariables(variables);
        }
        
        changeBuilder.changeState();
        
        LOGGER.debug("Completed parallel gateway step back");
    }

    /**
     * Executes a step back operation in a subprocess scenario.
     */
    private void executeSubProcessStepBack(String currentTaskId, String targetTaskId, 
            ExecutionEntity processInstanceExecution, Map<String, Object> variables) {
        
        LOGGER.debug("Executing subprocess step back from {} to {}", currentTaskId, targetTaskId);
        
        // For subprocess scenarios, we need to handle the scope appropriately
        ChangeActivityStateBuilder changeBuilder = new ChangeActivityStateBuilderImpl()
                .processInstanceId(processInstanceExecution.getId())
                .moveActivityIdTo(currentTaskId, targetTaskId);
        
        if (variables != null && !variables.isEmpty()) {
            changeBuilder.processVariables(variables);
        }
        
        changeBuilder.changeState();
        
        LOGGER.debug("Completed subprocess step back");
    }

    /**
     * Enumeration of step back strategies.
     */
    private enum StepBackStrategy {
        SIMPLE,
        PARALLEL_GATEWAY,
        SUBPROCESS
    }
}