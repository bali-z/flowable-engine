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

import java.util.*;

import org.flowable.bpmn.model.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.common.engine.impl.service.CommonEngineServiceImpl;
import org.flowable.engine.WorkflowStepBackService;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.engine.impl.util.PreviousTaskFinder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of WorkflowStepBackService providing workflow navigation capabilities.
 * 
 * @author Flowable Team
 */
public class WorkflowStepBackServiceImpl extends CommonEngineServiceImpl<ProcessEngineConfigurationImpl> implements WorkflowStepBackService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStepBackServiceImpl.class);

    public WorkflowStepBackServiceImpl(ProcessEngineConfigurationImpl processEngineConfiguration) {
        super(processEngineConfiguration);
    }

    @Override
    public HistoricTaskInstance getPreviousTaskInSequentialFlow(String currentTaskId) {
        LOGGER.debug("Getting previous task in sequential flow for task: {}", currentTaskId);
        
        if (currentTaskId == null) {
            throw new FlowableException("Current task ID cannot be null");
        }

        return commandExecutor.execute(commandContext -> {
            // Validate task exists
            HistoricTaskInstance currentTask = validateTaskExists(currentTaskId, commandContext);
            
            LOGGER.info("Finding previous task in sequential flow for task: {} in process instance: {}", 
                    currentTaskId, currentTask.getProcessInstanceId());
            
            return PreviousTaskFinder.findPreviousTaskInSequentialFlow(
                    currentTaskId, 
                    currentTask.getProcessInstanceId(), 
                    commandContext);
        });
    }

    @Override
    public List<HistoricTaskInstance> getPreviousTasksInParallelGateway(String currentTaskId) {
        LOGGER.debug("Getting previous tasks in parallel gateway for task: {}", currentTaskId);
        
        if (currentTaskId == null) {
            throw new FlowableException("Current task ID cannot be null");
        }

        return commandExecutor.execute(commandContext -> {
            // Validate task exists
            HistoricTaskInstance currentTask = validateTaskExists(currentTaskId, commandContext);
            
            LOGGER.info("Finding previous tasks in parallel gateway for task: {} in process instance: {}", 
                    currentTaskId, currentTask.getProcessInstanceId());
            
            return PreviousTaskFinder.findPreviousTasksInParallelGateway(
                    currentTaskId, 
                    currentTask.getProcessInstanceId(), 
                    commandContext);
        });
    }

    @Override
    public List<HistoricTaskInstance> getPreviousTasksInSubprocess(String currentTaskId) {
        LOGGER.debug("Getting previous tasks in subprocess for task: {}", currentTaskId);
        
        if (currentTaskId == null) {
            throw new FlowableException("Current task ID cannot be null");
        }

        return commandExecutor.execute(commandContext -> {
            // Validate task exists
            HistoricTaskInstance currentTask = validateTaskExists(currentTaskId, commandContext);
            
            LOGGER.info("Finding previous tasks in subprocess for task: {} in process instance: {}", 
                    currentTaskId, currentTask.getProcessInstanceId());
            
            return PreviousTaskFinder.findPreviousTasksInSubprocess(
                    currentTaskId, 
                    currentTask.getProcessInstanceId(), 
                    commandContext);
        });
    }

    @Override
    public List<HistoricTaskInstance> getAllPreviousTasks(String currentTaskId) {
        LOGGER.debug("Getting all previous tasks with automatic scenario detection for task: {}", currentTaskId);
        
        if (currentTaskId == null) {
            throw new FlowableException("Current task ID cannot be null");
        }

        return commandExecutor.execute(commandContext -> {
            // Validate task exists
            HistoricTaskInstance currentTask = validateTaskExists(currentTaskId, commandContext);
            
            LOGGER.info("Auto-detecting scenario and finding all previous tasks for task: {} in process instance: {}", 
                    currentTaskId, currentTask.getProcessInstanceId());
            
            // Detect scenario type and apply appropriate strategy
            StepBackScenario scenario = detectScenario(currentTask, commandContext);
            LOGGER.debug("Detected scenario: {} for task: {}", scenario, currentTaskId);
            
            List<HistoricTaskInstance> allPreviousTasks = new ArrayList<>();
            
            switch (scenario) {
                case SEQUENTIAL:
                    HistoricTaskInstance sequentialTask = PreviousTaskFinder.findPreviousTaskInSequentialFlow(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext);
                    if (sequentialTask != null) {
                        allPreviousTasks.add(sequentialTask);
                    }
                    break;
                    
                case PARALLEL:
                    allPreviousTasks.addAll(PreviousTaskFinder.findPreviousTasksInParallelGateway(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext));
                    break;
                    
                case SUBPROCESS:
                    allPreviousTasks.addAll(PreviousTaskFinder.findPreviousTasksInSubprocess(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext));
                    break;
                    
                case MIXED:
                    // Try all strategies and combine results
                    HistoricTaskInstance seqTask = PreviousTaskFinder.findPreviousTaskInSequentialFlow(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext);
                    if (seqTask != null) {
                        allPreviousTasks.add(seqTask);
                    }
                    
                    allPreviousTasks.addAll(PreviousTaskFinder.findPreviousTasksInParallelGateway(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext));
                    
                    allPreviousTasks.addAll(PreviousTaskFinder.findPreviousTasksInSubprocess(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext));
                    
                    // Remove duplicates
                    allPreviousTasks = removeDuplicateTasks(allPreviousTasks);
                    break;
                    
                default:
                    LOGGER.warn("Unknown scenario detected for task: {}, trying all strategies", currentTaskId);
                    // Fall back to trying all strategies
                    HistoricTaskInstance unknownSeqTask = PreviousTaskFinder.findPreviousTaskInSequentialFlow(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext);
                    if (unknownSeqTask != null) {
                        allPreviousTasks.add(unknownSeqTask);
                    }
                    
                    allPreviousTasks.addAll(PreviousTaskFinder.findPreviousTasksInParallelGateway(
                            currentTaskId, currentTask.getProcessInstanceId(), commandContext));
                    
                    allPreviousTasks = removeDuplicateTasks(allPreviousTasks);
                    break;
            }
            
            // Sort by end time (most recent first)
            allPreviousTasks.sort((t1, t2) -> {
                if (t1.getEndTime() == null && t2.getEndTime() == null) return 0;
                if (t1.getEndTime() == null) return 1;
                if (t2.getEndTime() == null) return -1;
                return t2.getEndTime().compareTo(t1.getEndTime());
            });
            
            LOGGER.info("Found {} previous tasks for task: {}", allPreviousTasks.size(), currentTaskId);
            return allPreviousTasks;
        });
    }

    @Override
    public boolean isStepBackAllowed(String currentTaskId) {
        StepBackValidationResult result = validateStepBack(currentTaskId);
        return result.isAllowed();
    }

    @Override
    public StepBackValidationResult validateStepBack(String currentTaskId) {
        LOGGER.debug("Validating step back for task: {}", currentTaskId);
        
        if (currentTaskId == null) {
            return new StepBackValidationResultImpl(false, 
                    Arrays.asList("Current task ID cannot be null"), 
                    Collections.emptyList(), 
                    StepBackScenario.UNKNOWN);
        }

        return commandExecutor.execute(commandContext -> {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            try {
                // Validate task exists
                HistoricTaskInstance currentTask = validateTaskExists(currentTaskId, commandContext);
                
                LOGGER.info("Validating step back for task: {} in process instance: {}", 
                        currentTaskId, currentTask.getProcessInstanceId());
                
                // Detect scenario
                StepBackScenario scenario = detectScenario(currentTask, commandContext);
                
                // Check if task is completed
                if (currentTask.getEndTime() == null) {
                    errors.add("Cannot step back from an active task. Task must be completed first.");
                }
                
                // Check if process instance exists and is accessible
                if (currentTask.getProcessInstanceId() == null) {
                    errors.add("Task is not associated with a process instance");
                }
                
                // Check process definition accessibility
                try {
                    ProcessDefinition processDefinition = configuration.getRepositoryService()
                            .getProcessDefinition(currentTask.getProcessDefinitionId());
                    if (processDefinition == null) {
                        errors.add("Process definition not found or not accessible");
                    }
                } catch (Exception e) {
                    errors.add("Error accessing process definition: " + e.getMessage());
                }
                
                // Scenario-specific validation
                switch (scenario) {
                    case SEQUENTIAL:
                        validateSequentialFlowStepBack(currentTask, commandContext, errors, warnings);
                        break;
                    case PARALLEL:
                        validateParallelGatewayStepBack(currentTask, commandContext, errors, warnings);
                        break;
                    case SUBPROCESS:
                        validateSubprocessStepBack(currentTask, commandContext, errors, warnings);
                        break;
                    case MIXED:
                        warnings.add("Complex mixed scenario detected. Step back behavior may be unpredictable.");
                        break;
                    default:
                        warnings.add("Could not determine flow scenario. Step back may not work as expected.");
                        break;
                }
                
                boolean isAllowed = errors.isEmpty();
                LOGGER.info("Step back validation for task {}: allowed={}, errors={}, warnings={}", 
                        currentTaskId, isAllowed, errors.size(), warnings.size());
                
                return new StepBackValidationResultImpl(isAllowed, errors, warnings, scenario);
                
            } catch (FlowableObjectNotFoundException e) {
                errors.add("Task not found: " + currentTaskId);
                return new StepBackValidationResultImpl(false, errors, warnings, StepBackScenario.UNKNOWN);
            } catch (Exception e) {
                LOGGER.error("Error during step back validation for task: " + currentTaskId, e);
                errors.add("Validation error: " + e.getMessage());
                return new StepBackValidationResultImpl(false, errors, warnings, StepBackScenario.UNKNOWN);
            }
        });
    }

    // Helper methods

    private HistoricTaskInstance validateTaskExists(String taskId, CommandContext commandContext) {
        HistoricTaskInstance task = CommandContextUtil.getProcessEngineConfiguration(commandContext)
                .getHistoryService()
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        
        if (task == null) {
            throw new FlowableObjectNotFoundException("Historic task not found with id: " + taskId);
        }
        
        return task;
    }

    private StepBackScenario detectScenario(HistoricTaskInstance currentTask, CommandContext commandContext) {
        try {
            ProcessDefinition processDefinition = configuration.getRepositoryService()
                    .getProcessDefinition(currentTask.getProcessDefinitionId());
            
            BpmnModel bpmnModel = configuration.getRepositoryService()
                    .getBpmnModel(processDefinition.getId());
            
            FlowElement currentFlowElement = bpmnModel.getFlowElement(currentTask.getTaskDefinitionKey());
            
            if (currentFlowElement == null || !(currentFlowElement instanceof UserTask)) {
                return StepBackScenario.UNKNOWN;
            }
            
            UserTask currentUserTask = (UserTask) currentFlowElement;
            
            // Check for subprocess
            if (isWithinSubprocess(currentFlowElement, bpmnModel)) {
                return StepBackScenario.SUBPROCESS;
            }
            
            // Check for parallel gateway in incoming flows
            if (hasParallelGatewayInIncomingFlows(currentUserTask)) {
                return StepBackScenario.PARALLEL;
            }
            
            // Check for mixed scenario
            if (hasMixedFlowPatterns(currentUserTask, bpmnModel)) {
                return StepBackScenario.MIXED;
            }
            
            // Default to sequential
            return StepBackScenario.SEQUENTIAL;
            
        } catch (Exception e) {
            LOGGER.warn("Error detecting scenario for task: " + currentTask.getId(), e);
            return StepBackScenario.UNKNOWN;
        }
    }

    private boolean isWithinSubprocess(FlowElement element, BpmnModel bpmnModel) {
        for (org.flowable.bpmn.model.Process process : bpmnModel.getProcesses()) {
            for (FlowElement flowElement : process.getFlowElements()) {
                if (flowElement instanceof SubProcess) {
                    if (isElementWithinSubprocess(element, (SubProcess) flowElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isElementWithinSubprocess(FlowElement element, SubProcess subprocess) {
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

    private boolean hasParallelGatewayInIncomingFlows(UserTask userTask) {
        for (SequenceFlow incomingFlow : userTask.getIncomingFlows()) {
            if (incomingFlow.getSourceFlowElement() instanceof ParallelGateway) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMixedFlowPatterns(UserTask userTask, BpmnModel bpmnModel) {
        // Check for multiple different types of gateways or complex patterns
        Set<Class<?>> gatewayTypes = new HashSet<>();
        return analyzeIncomingFlowComplexity(userTask, gatewayTypes, new HashSet<>()) > 1;
    }

    private int analyzeIncomingFlowComplexity(FlowNode flowNode, Set<Class<?>> gatewayTypes, Set<String> visited) {
        if (visited.contains(flowNode.getId())) {
            return 0;
        }
        visited.add(flowNode.getId());
        
        int complexity = 0;
        
        for (SequenceFlow incomingFlow : flowNode.getIncomingFlows()) {
            FlowElement sourceElement = incomingFlow.getSourceFlowElement();
            
            if (sourceElement instanceof Gateway) {
                gatewayTypes.add(sourceElement.getClass());
                complexity++;
            }
            
            if (sourceElement instanceof FlowNode) {
                complexity += analyzeIncomingFlowComplexity((FlowNode) sourceElement, gatewayTypes, visited);
            }
        }
        
        return complexity;
    }

    private void validateSequentialFlowStepBack(HistoricTaskInstance currentTask, CommandContext commandContext, List<String> errors, List<String> warnings) {
        // Check if there are any previous tasks
        HistoricTaskInstance previousTask = PreviousTaskFinder.findPreviousTaskInSequentialFlow(
                currentTask.getId(), currentTask.getProcessInstanceId(), commandContext);
        
        if (previousTask == null) {
            warnings.add("No previous task found in sequential flow. This may be the first task in the process.");
        }
    }

    private void validateParallelGatewayStepBack(HistoricTaskInstance currentTask, CommandContext commandContext, List<String> errors, List<String> warnings) {
        // Check if there are any previous tasks in parallel branches
        List<HistoricTaskInstance> previousTasks = PreviousTaskFinder.findPreviousTasksInParallelGateway(
                currentTask.getId(), currentTask.getProcessInstanceId(), commandContext);
        
        if (previousTasks.isEmpty()) {
            warnings.add("No previous tasks found in parallel branches. All parallel paths may have been empty or contained only automatic tasks.");
        } else if (previousTasks.size() == 1) {
            warnings.add("Only one previous task found in parallel scenario. This may indicate incomplete parallel execution.");
        }
    }

    private void validateSubprocessStepBack(HistoricTaskInstance currentTask, CommandContext commandContext, List<String> errors, List<String> warnings) {
        // Check if there are any previous tasks in subprocess
        List<HistoricTaskInstance> previousTasks = PreviousTaskFinder.findPreviousTasksInSubprocess(
                currentTask.getId(), currentTask.getProcessInstanceId(), commandContext);
        
        if (previousTasks.isEmpty()) {
            warnings.add("No previous tasks found in subprocess hierarchy. This may be the first task in the subprocess.");
        }
        
        warnings.add("Subprocess step back may have complex behavior across subprocess boundaries.");
    }

    private List<HistoricTaskInstance> removeDuplicateTasks(List<HistoricTaskInstance> tasks) {
        Map<String, HistoricTaskInstance> uniqueTasks = new LinkedHashMap<>();
        for (HistoricTaskInstance task : tasks) {
            uniqueTasks.put(task.getId(), task);
        }
        return new ArrayList<>(uniqueTasks.values());
    }

    // Implementation of StepBackValidationResult
    private static class StepBackValidationResultImpl implements StepBackValidationResult {
        private final boolean allowed;
        private final List<String> validationErrors;
        private final List<String> warnings;
        private final StepBackScenario scenario;

        public StepBackValidationResultImpl(boolean allowed, List<String> validationErrors, List<String> warnings, StepBackScenario scenario) {
            this.allowed = allowed;
            this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
            this.warnings = warnings != null ? warnings : Collections.emptyList();
            this.scenario = scenario;
        }

        @Override
        public boolean isAllowed() {
            return allowed;
        }

        @Override
        public List<String> getValidationErrors() {
            return validationErrors;
        }

        @Override
        public List<String> getWarnings() {
            return warnings;
        }

        @Override
        public StepBackScenario getScenario() {
            return scenario;
        }
    }
}