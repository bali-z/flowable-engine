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

import java.util.*;

import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for finding previous tasks in various BPMN flow scenarios.
 * 
 * This class provides methods to navigate backwards through a process flow to find 
 * previously executed tasks. It handles different types of flow constructs including:
 * - Sequential flows (simple task sequences)
 * - Parallel gateways (fork/join patterns)
 * - Subprocesses (embedded and call activities)
 * 
 * @author Flowable Team
 */
public class PreviousTaskFinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreviousTaskFinder.class);

    /**
     * Finds the previous task in a sequential flow.
     * 
     * In a sequential flow, there is typically one incoming sequence flow to a task,
     * and we follow it backwards to find the previous user task.
     * 
     * @param currentTaskId the ID of the current task
     * @param processInstanceId the process instance ID
     * @param commandContext the command context
     * @return the previous task if found, null otherwise
     */
    public static HistoricTaskInstance findPreviousTaskInSequentialFlow(String currentTaskId, String processInstanceId, CommandContext commandContext) {
        LOGGER.debug("Finding previous task in sequential flow for task: {} in process instance: {}", currentTaskId, processInstanceId);
        
        try {
            HistoricTaskInstance currentTask = getCurrentHistoricTask(currentTaskId, commandContext);
            if (currentTask == null) {
                LOGGER.warn("Current task not found: {}", currentTaskId);
                return null;
            }

            // Get the process definition and find the current task's flow element
            ProcessDefinition processDefinition = getProcessDefinition(currentTask.getProcessDefinitionId(), commandContext);
            BpmnModel bpmnModel = getBpmnModel(processDefinition, commandContext);
            
            FlowElement currentFlowElement = bpmnModel.getFlowElement(currentTask.getTaskDefinitionKey());
            if (!(currentFlowElement instanceof UserTask)) {
                LOGGER.warn("Current flow element is not a user task: {}", currentTask.getTaskDefinitionKey());
                return null;
            }

            UserTask currentUserTask = (UserTask) currentFlowElement;
            
            // Navigate backwards through the sequence flows
            List<SequenceFlow> incomingFlows = currentUserTask.getIncomingFlows();
            
            for (SequenceFlow incomingFlow : incomingFlows) {
                FlowElement sourceElement = incomingFlow.getSourceFlowElement();
                HistoricTaskInstance previousTask = findPreviousUserTaskFromElement(sourceElement, processInstanceId, currentTask.getEndTime(), commandContext, bpmnModel);
                if (previousTask != null) {
                    LOGGER.debug("Found previous task in sequential flow: {}", previousTask.getId());
                    return previousTask;
                }
            }
            
            LOGGER.debug("No previous task found in sequential flow for task: {}", currentTaskId);
            return null;
            
        } catch (Exception e) {
            LOGGER.error("Error finding previous task in sequential flow for task: " + currentTaskId, e);
            throw new FlowableException("Error finding previous task in sequential flow", e);
        }
    }

    /**
     * Finds previous tasks in parallel gateway scenarios.
     * 
     * In parallel gateways, multiple tasks can execute concurrently. This method
     * finds all tasks that were executed in parallel branches before the current task.
     * 
     * @param currentTaskId the ID of the current task
     * @param processInstanceId the process instance ID
     * @param commandContext the command context
     * @return list of previous tasks from parallel branches
     */
    public static List<HistoricTaskInstance> findPreviousTasksInParallelGateway(String currentTaskId, String processInstanceId, CommandContext commandContext) {
        LOGGER.debug("Finding previous tasks in parallel gateway for task: {} in process instance: {}", currentTaskId, processInstanceId);
        
        List<HistoricTaskInstance> previousTasks = new ArrayList<>();
        
        try {
            HistoricTaskInstance currentTask = getCurrentHistoricTask(currentTaskId, commandContext);
            if (currentTask == null) {
                LOGGER.warn("Current task not found: {}", currentTaskId);
                return previousTasks;
            }

            ProcessDefinition processDefinition = getProcessDefinition(currentTask.getProcessDefinitionId(), commandContext);
            BpmnModel bpmnModel = getBpmnModel(processDefinition, commandContext);
            
            FlowElement currentFlowElement = bpmnModel.getFlowElement(currentTask.getTaskDefinitionKey());
            if (!(currentFlowElement instanceof UserTask)) {
                LOGGER.warn("Current flow element is not a user task: {}", currentTask.getTaskDefinitionKey());
                return previousTasks;
            }

            UserTask currentUserTask = (UserTask) currentFlowElement;
            
            // Look for parallel gateways in the incoming flows
            Set<String> visitedElements = new HashSet<>();
            findTasksFromParallelBranches(currentUserTask, processInstanceId, currentTask.getEndTime(), commandContext, bpmnModel, previousTasks, visitedElements);
            
            LOGGER.debug("Found {} previous tasks in parallel gateway for task: {}", previousTasks.size(), currentTaskId);
            return previousTasks;
            
        } catch (Exception e) {
            LOGGER.error("Error finding previous tasks in parallel gateway for task: " + currentTaskId, e);
            throw new FlowableException("Error finding previous tasks in parallel gateway", e);
        }
    }

    /**
     * Finds previous tasks in subprocess scenarios.
     * 
     * In subprocesses, tasks can be contained within embedded subprocesses or
     * referenced through call activities. This method navigates the subprocess
     * hierarchy to find previous tasks.
     * 
     * @param currentTaskId the ID of the current task
     * @param processInstanceId the process instance ID
     * @param commandContext the command context
     * @return list of previous tasks from subprocess hierarchy
     */
    public static List<HistoricTaskInstance> findPreviousTasksInSubprocess(String currentTaskId, String processInstanceId, CommandContext commandContext) {
        LOGGER.debug("Finding previous tasks in subprocess for task: {} in process instance: {}", currentTaskId, processInstanceId);
        
        List<HistoricTaskInstance> previousTasks = new ArrayList<>();
        
        try {
            HistoricTaskInstance currentTask = getCurrentHistoricTask(currentTaskId, commandContext);
            if (currentTask == null) {
                LOGGER.warn("Current task not found: {}", currentTaskId);
                return previousTasks;
            }

            ProcessDefinition processDefinition = getProcessDefinition(currentTask.getProcessDefinitionId(), commandContext);
            BpmnModel bpmnModel = getBpmnModel(processDefinition, commandContext);
            
            // Check if the current task is within a subprocess
            FlowElement currentFlowElement = bpmnModel.getFlowElement(currentTask.getTaskDefinitionKey());
            if (currentFlowElement == null) {
                LOGGER.warn("Current flow element not found: {}", currentTask.getTaskDefinitionKey());
                return previousTasks;
            }

            // Find the subprocess container
            SubProcess containingSubprocess = findContainingSubprocess(currentFlowElement, bpmnModel);
            
            if (containingSubprocess != null) {
                LOGGER.debug("Task is within subprocess: {}", containingSubprocess.getId());
                
                // First, look for tasks within the same subprocess
                findPreviousTasksWithinSubprocess(containingSubprocess, currentTask, processInstanceId, commandContext, previousTasks);
                
                // Then, look for tasks before the subprocess started
                findTasksBeforeSubprocess(containingSubprocess, processInstanceId, currentTask.getCreateTime(), commandContext, bpmnModel, previousTasks);
            } else {
                // Check if this process instance is a subprocess itself (call activity)
                findTasksFromParentProcess(processInstanceId, currentTask.getCreateTime(), commandContext, previousTasks);
            }
            
            LOGGER.debug("Found {} previous tasks in subprocess for task: {}", previousTasks.size(), currentTaskId);
            return previousTasks;
            
        } catch (Exception e) {
            LOGGER.error("Error finding previous tasks in subprocess for task: " + currentTaskId, e);
            throw new FlowableException("Error finding previous tasks in subprocess", e);
        }
    }

    // Helper methods

    private static HistoricTaskInstance getCurrentHistoricTask(String taskId, CommandContext commandContext) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        return processEngineConfiguration.getHistoryService().createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
    }

    private static ProcessDefinition getProcessDefinition(String processDefinitionId, CommandContext commandContext) {
        return CommandContextUtil.getProcessEngineConfiguration(commandContext)
                .getRepositoryService()
                .getProcessDefinition(processDefinitionId);
    }

    private static BpmnModel getBpmnModel(ProcessDefinition processDefinition, CommandContext commandContext) {
        return CommandContextUtil.getProcessEngineConfiguration(commandContext)
                .getRepositoryService()
                .getBpmnModel(processDefinition.getId());
    }

    private static HistoricTaskInstance findPreviousUserTaskFromElement(FlowElement element, String processInstanceId, Date beforeTime, CommandContext commandContext, BpmnModel bpmnModel) {
        if (element instanceof UserTask) {
            UserTask userTask = (UserTask) element;
            return findHistoricTaskByDefinitionKey(userTask.getId(), processInstanceId, beforeTime, commandContext);
        } else if (element instanceof Gateway) {
            // Navigate through gateway
            return navigateBackwardsThroughGateway((Gateway) element, processInstanceId, beforeTime, commandContext, bpmnModel);
        } else if (element instanceof FlowNode) {
            // Continue navigating backwards
            FlowNode flowNode = (FlowNode) element;
            for (SequenceFlow incomingFlow : flowNode.getIncomingFlows()) {
                HistoricTaskInstance task = findPreviousUserTaskFromElement(incomingFlow.getSourceFlowElement(), processInstanceId, beforeTime, commandContext, bpmnModel);
                if (task != null) {
                    return task;
                }
            }
        }
        return null;
    }

    private static HistoricTaskInstance navigateBackwardsThroughGateway(Gateway gateway, String processInstanceId, Date beforeTime, CommandContext commandContext, BpmnModel bpmnModel) {
        for (SequenceFlow incomingFlow : gateway.getIncomingFlows()) {
            HistoricTaskInstance task = findPreviousUserTaskFromElement(incomingFlow.getSourceFlowElement(), processInstanceId, beforeTime, commandContext, bpmnModel);
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    private static void findTasksFromParallelBranches(UserTask currentTask, String processInstanceId, Date beforeTime, CommandContext commandContext, BpmnModel bpmnModel, List<HistoricTaskInstance> previousTasks, Set<String> visitedElements) {
        if (visitedElements.contains(currentTask.getId())) {
            return;
        }
        visitedElements.add(currentTask.getId());

        for (SequenceFlow incomingFlow : currentTask.getIncomingFlows()) {
            FlowElement sourceElement = incomingFlow.getSourceFlowElement();
            
            if (sourceElement instanceof ParallelGateway) {
                ParallelGateway gateway = (ParallelGateway) sourceElement;
                
                // Find all branches that converge at this gateway
                for (SequenceFlow gatewayIncomingFlow : gateway.getIncomingFlows()) {
                    collectTasksFromBranch(gatewayIncomingFlow.getSourceFlowElement(), processInstanceId, beforeTime, commandContext, bpmnModel, previousTasks, visitedElements);
                }
            } else {
                findTasksFromParallelBranches((UserTask) sourceElement, processInstanceId, beforeTime, commandContext, bpmnModel, previousTasks, visitedElements);
            }
        }
    }

    private static void collectTasksFromBranch(FlowElement element, String processInstanceId, Date beforeTime, CommandContext commandContext, BpmnModel bpmnModel, List<HistoricTaskInstance> previousTasks, Set<String> visitedElements) {
        if (element == null || visitedElements.contains(element.getId())) {
            return;
        }
        visitedElements.add(element.getId());

        if (element instanceof UserTask) {
            UserTask userTask = (UserTask) element;
            HistoricTaskInstance historicTask = findHistoricTaskByDefinitionKey(userTask.getId(), processInstanceId, beforeTime, commandContext);
            if (historicTask != null && !containsTaskWithId(previousTasks, historicTask.getId())) {
                previousTasks.add(historicTask);
            }
        }

        if (element instanceof FlowNode) {
            FlowNode flowNode = (FlowNode) element;
            for (SequenceFlow incomingFlow : flowNode.getIncomingFlows()) {
                collectTasksFromBranch(incomingFlow.getSourceFlowElement(), processInstanceId, beforeTime, commandContext, bpmnModel, previousTasks, visitedElements);
            }
        }
    }

    private static boolean containsTaskWithId(List<HistoricTaskInstance> tasks, String taskId) {
        return tasks.stream().anyMatch(task -> task.getId().equals(taskId));
    }

    private static HistoricTaskInstance findHistoricTaskByDefinitionKey(String taskDefinitionKey, String processInstanceId, Date beforeTime, CommandContext commandContext) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        
        List<HistoricTaskInstance> tasks = processEngineConfiguration.getHistoryService().createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .taskCompletedBefore(beforeTime)
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();
        
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    private static SubProcess findContainingSubprocess(FlowElement element, BpmnModel bpmnModel) {
        for (org.flowable.bpmn.model.Process process : bpmnModel.getProcesses()) {
            for (FlowElement flowElement : process.getFlowElements()) {
                if (flowElement instanceof SubProcess) {
                    SubProcess subprocess = (SubProcess) flowElement;
                    if (isElementWithinSubprocess(element, subprocess)) {
                        return subprocess;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isElementWithinSubprocess(FlowElement element, SubProcess subprocess) {
        for (FlowElement subElement : subprocess.getFlowElements()) {
            if (subElement.getId().equals(element.getId())) {
                return true;
            }
            if (subElement instanceof SubProcess) {
                if (isElementWithinSubprocess(element, (SubProcess) subElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void findPreviousTasksWithinSubprocess(SubProcess subprocess, HistoricTaskInstance currentTask, String processInstanceId, CommandContext commandContext, List<HistoricTaskInstance> previousTasks) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        
        List<HistoricTaskInstance> tasks = processEngineConfiguration.getHistoryService().createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskCompletedBefore(currentTask.getCreateTime())
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();
        
        // Filter tasks that belong to the same subprocess
        for (HistoricTaskInstance task : tasks) {
            if (isTaskInSubprocess(task.getTaskDefinitionKey(), subprocess)) {
                previousTasks.add(task);
            }
        }
    }

    private static boolean isTaskInSubprocess(String taskDefinitionKey, SubProcess subprocess) {
        for (FlowElement element : subprocess.getFlowElements()) {
            if (element instanceof UserTask && element.getId().equals(taskDefinitionKey)) {
                return true;
            }
            if (element instanceof SubProcess) {
                if (isTaskInSubprocess(taskDefinitionKey, (SubProcess) element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void findTasksBeforeSubprocess(SubProcess subprocess, String processInstanceId, Date beforeTime, CommandContext commandContext, BpmnModel bpmnModel, List<HistoricTaskInstance> previousTasks) {
        // Navigate backwards from subprocess start
        for (SequenceFlow incomingFlow : subprocess.getIncomingFlows()) {
            HistoricTaskInstance task = findPreviousUserTaskFromElement(incomingFlow.getSourceFlowElement(), processInstanceId, beforeTime, commandContext, bpmnModel);
            if (task != null && !containsTaskWithId(previousTasks, task.getId())) {
                previousTasks.add(task);
            }
        }
    }

    private static void findTasksFromParentProcess(String processInstanceId, Date beforeTime, CommandContext commandContext, List<HistoricTaskInstance> previousTasks) {
        ProcessEngineConfigurationImpl processEngineConfiguration = CommandContextUtil.getProcessEngineConfiguration(commandContext);
        
        // Check if this process instance has a super process instance (call activity)
        // This is a simplified approach - in a real implementation, you'd need to check
        // the entity links or process hierarchy
        
        LOGGER.debug("Checking for parent process tasks for subprocess instance: {}", processInstanceId);
        // Implementation would depend on how call activities and subprocess relationships are tracked
        // This is a placeholder for the actual implementation
    }
}