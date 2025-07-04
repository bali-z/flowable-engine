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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for finding previous tasks in a workflow process.
 * This class analyzes the BPMN model to determine possible previous task nodes
 * that can be used for step back operations.
 * 
 * @author Flowable Team
 */
public class PreviousTaskFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreviousTaskFinder.class);

    /**
     * Finds all possible previous user tasks for a given current task.
     * 
     * @param currentTaskId the current task identifier
     * @param executionEntity the execution entity associated with the current task
     * @return list of previous task identifiers that can be used for step back
     */
    public static List<String> findPreviousTasks(String currentTaskId, ExecutionEntity executionEntity) {
        if (currentTaskId == null || executionEntity == null) {
            throw new FlowableException("Current task ID and execution entity are required");
        }

        try {
            BpmnModel bpmnModel = ProcessDefinitionUtil.getBpmnModel(executionEntity.getProcessDefinitionId());
            FlowElement currentElement = bpmnModel.getFlowElement(currentTaskId);
            
            if (currentElement == null) {
                throw new FlowableException("Could not find flow element for task ID: " + currentTaskId);
            }

            if (!(currentElement instanceof UserTask)) {
                throw new FlowableException("Current element is not a user task: " + currentTaskId);
            }

            List<String> previousTasks = new ArrayList<>();
            Set<String> visitedElements = new HashSet<>();
            
            findPreviousTasksRecursive(currentElement, bpmnModel, previousTasks, visitedElements);
            
            LOGGER.debug("Found {} previous tasks for current task {}: {}", 
                    previousTasks.size(), currentTaskId, previousTasks);
            
            return previousTasks;
            
        } catch (Exception e) {
            LOGGER.error("Error finding previous tasks for task ID: {}", currentTaskId, e);
            throw new FlowableException("Failed to find previous tasks", e);
        }
    }

    /**
     * Recursively finds previous user tasks by traversing incoming sequence flows.
     */
    private static void findPreviousTasksRecursive(FlowElement currentElement, BpmnModel bpmnModel, 
            List<String> previousTasks, Set<String> visitedElements) {
        
        if (currentElement == null || visitedElements.contains(currentElement.getId())) {
            return;
        }
        
        visitedElements.add(currentElement.getId());
        
        if (currentElement instanceof FlowNode) {
            FlowNode flowNode = (FlowNode) currentElement;
            
            for (SequenceFlow incomingFlow : flowNode.getIncomingFlows()) {
                FlowElement sourceElement = bpmnModel.getFlowElement(incomingFlow.getSourceRef());
                
                if (sourceElement instanceof UserTask) {
                    // Found a previous user task
                    if (!previousTasks.contains(sourceElement.getId())) {
                        previousTasks.add(sourceElement.getId());
                        LOGGER.debug("Found previous user task: {}", sourceElement.getId());
                    }
                } else if (sourceElement instanceof Gateway) {
                    // Handle gateway scenarios
                    handleGatewayScenario(sourceElement, bpmnModel, previousTasks, visitedElements);
                } else if (sourceElement instanceof SubProcess) {
                    // Handle subprocess scenarios
                    handleSubProcessScenario(sourceElement, bpmnModel, previousTasks, visitedElements);
                } else {
                    // Continue traversing for other flow elements
                    findPreviousTasksRecursive(sourceElement, bpmnModel, previousTasks, visitedElements);
                }
            }
        }
    }

    /**
     * Handles gateway scenarios including parallel gateways.
     */
    private static void handleGatewayScenario(FlowElement gatewayElement, BpmnModel bpmnModel, 
            List<String> previousTasks, Set<String> visitedElements) {
        
        LOGGER.debug("Handling gateway scenario for element: {}", gatewayElement.getId());
        
        if (gatewayElement instanceof ParallelGateway) {
            // For parallel gateways, we need to find tasks from all incoming branches
            ParallelGateway gateway = (ParallelGateway) gatewayElement;
            
            for (SequenceFlow incomingFlow : gateway.getIncomingFlows()) {
                FlowElement sourceElement = bpmnModel.getFlowElement(incomingFlow.getSourceRef());
                findPreviousTasksRecursive(sourceElement, bpmnModel, previousTasks, visitedElements);
            }
        } else {
            // For other gateways, continue normal traversal
            findPreviousTasksRecursive(gatewayElement, bpmnModel, previousTasks, visitedElements);
        }
    }

    /**
     * Handles subprocess scenarios.
     */
    private static void handleSubProcessScenario(FlowElement subProcessElement, BpmnModel bpmnModel, 
            List<String> previousTasks, Set<String> visitedElements) {
        
        LOGGER.debug("Handling subprocess scenario for element: {}", subProcessElement.getId());
        
        if (subProcessElement instanceof SubProcess) {
            SubProcess subProcess = (SubProcess) subProcessElement;
            
            // Look for user tasks within the subprocess
            for (FlowElement flowElement : subProcess.getFlowElements()) {
                if (flowElement instanceof UserTask) {
                    if (!previousTasks.contains(flowElement.getId())) {
                        previousTasks.add(flowElement.getId());
                        LOGGER.debug("Found previous user task in subprocess: {}", flowElement.getId());
                    }
                }
            }
            
            // Also traverse incoming flows to the subprocess
            if (subProcessElement instanceof FlowNode) {
                findPreviousTasksRecursive(subProcessElement, bpmnModel, previousTasks, visitedElements);
            }
        }
    }

    /**
     * Finds the most recent previous task based on execution history.
     * This is useful when multiple previous tasks are found and we need to determine
     * the most logical step back target.
     */
    public static String findMostRecentPreviousTask(List<String> previousTasks, ExecutionEntity executionEntity) {
        if (previousTasks == null || previousTasks.isEmpty()) {
            return null;
        }
        
        if (previousTasks.size() == 1) {
            return previousTasks.get(0);
        }
        
        // For now, return the first task. In a more sophisticated implementation,
        // this could analyze execution history to determine the most recently completed task
        LOGGER.debug("Multiple previous tasks found, returning first: {}", previousTasks.get(0));
        return previousTasks.get(0);
    }
}