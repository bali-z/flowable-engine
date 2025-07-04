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
package org.flowable.engine.test.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flowable.engine.WorkflowStepBackService;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.test.Deployment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.Test;

/**
 * Test cases for WorkflowStepBackService functionality.
 * 
 * @author Flowable Team
 */
public class WorkflowStepBackServiceTest extends PluggableFlowableTestCase {

    @Test
    @Deployment(resources = { "org/flowable/engine/test/workflow/sequential-flow-process.bpmn20.xml" })
    public void testGetPreviousTaskInSequentialFlow() {
        // Start a process instance
        runtimeService.startProcessInstanceByKey("sequentialFlowProcess");

        // Complete the first task
        Task task1 = taskService.createTaskQuery().singleResult();
        assertThat(task1.getName()).isEqualTo("Task 1");
        taskService.complete(task1.getId());

        // Get the second task
        Task task2 = taskService.createTaskQuery().singleResult();
        assertThat(task2.getName()).isEqualTo("Task 2");
        taskService.complete(task2.getId());

        // Get the third task
        Task task3 = taskService.createTaskQuery().singleResult();
        assertThat(task3.getName()).isEqualTo("Task 3");

        // Test: Get previous task in sequential flow
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        HistoricTaskInstance previousTask = workflowStepBackService.getPreviousTaskInSequentialFlow(task3.getId());

        // Verify that we get Task 2 as the previous task
        assertThat(previousTask).isNotNull();
        assertThat(previousTask.getName()).isEqualTo("Task 2");
        assertThat(previousTask.getId()).isEqualTo(task2.getId());
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/workflow/parallel-gateway-process.bpmn20.xml" })
    public void testGetPreviousTasksInParallelGateway() {
        // Start a process instance
        runtimeService.startProcessInstanceByKey("parallelGatewayProcess");

        // Complete the initial task
        Task initialTask = taskService.createTaskQuery().singleResult();
        assertThat(initialTask.getName()).isEqualTo("Initial Task");
        taskService.complete(initialTask.getId());

        // Get and complete parallel tasks
        List<Task> parallelTasks = taskService.createTaskQuery().orderByTaskName().asc().list();
        assertThat(parallelTasks).hasSize(2);
        assertThat(parallelTasks.get(0).getName()).isEqualTo("Parallel Task A");
        assertThat(parallelTasks.get(1).getName()).isEqualTo("Parallel Task B");

        taskService.complete(parallelTasks.get(0).getId());
        taskService.complete(parallelTasks.get(1).getId());

        // Get the final task
        Task finalTask = taskService.createTaskQuery().singleResult();
        assertThat(finalTask.getName()).isEqualTo("Final Task");

        // Test: Get previous tasks in parallel gateway
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        List<HistoricTaskInstance> previousTasks = workflowStepBackService.getPreviousTasksInParallelGateway(finalTask.getId());

        // Verify that we get both parallel tasks
        assertThat(previousTasks).hasSize(2);
        assertThat(previousTasks).extracting(HistoricTaskInstance::getName)
                .containsExactlyInAnyOrder("Parallel Task A", "Parallel Task B");
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/workflow/sequential-flow-process.bpmn20.xml" })
    public void testGetAllPreviousTasks() {
        // Start a process instance
        runtimeService.startProcessInstanceByKey("sequentialFlowProcess");

        // Complete the first task
        Task task1 = taskService.createTaskQuery().singleResult();
        taskService.complete(task1.getId());

        // Complete the second task
        Task task2 = taskService.createTaskQuery().singleResult();
        taskService.complete(task2.getId());

        // Get the third task
        Task task3 = taskService.createTaskQuery().singleResult();

        // Test: Get all previous tasks with automatic scenario detection
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        List<HistoricTaskInstance> allPreviousTasks = workflowStepBackService.getAllPreviousTasks(task3.getId());

        // In a sequential flow, we should get the immediate previous task
        assertThat(allPreviousTasks).hasSize(1);
        assertThat(allPreviousTasks.get(0).getName()).isEqualTo("Task 2");
    }

    @Test
    @Deployment(resources = { "org/flowable/engine/test/workflow/sequential-flow-process.bpmn20.xml" })
    public void testValidateStepBack() {
        // Start a process instance
        runtimeService.startProcessInstanceByKey("sequentialFlowProcess");

        // Get the first task (active task)
        Task activeTask = taskService.createTaskQuery().singleResult();

        // Test: Validate step back for active task (should not be allowed)
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        boolean isAllowed = workflowStepBackService.isStepBackAllowed(activeTask.getId());
        assertThat(isAllowed).isFalse();

        // Complete the task
        taskService.complete(activeTask.getId());

        // Test: Validate step back for completed task (should be allowed)
        WorkflowStepBackService.StepBackValidationResult validationResult = 
                workflowStepBackService.validateStepBack(activeTask.getId());
        
        assertThat(validationResult.isAllowed()).isFalse(); // First task has no previous task
        assertThat(validationResult.getScenario()).isEqualTo(WorkflowStepBackService.StepBackScenario.SEQUENTIAL);
        assertThat(validationResult.getWarnings()).isNotEmpty();
    }

    @Test
    public void testValidateStepBackWithNullTaskId() {
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        
        // Test: Validate step back with null task ID
        WorkflowStepBackService.StepBackValidationResult validationResult = 
                workflowStepBackService.validateStepBack(null);
        
        assertThat(validationResult.isAllowed()).isFalse();
        assertThat(validationResult.getValidationErrors()).contains("Current task ID cannot be null");
        assertThat(validationResult.getScenario()).isEqualTo(WorkflowStepBackService.StepBackScenario.UNKNOWN);
    }

    @Test
    public void testValidateStepBackWithNonExistentTaskId() {
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        
        // Test: Validate step back with non-existent task ID
        WorkflowStepBackService.StepBackValidationResult validationResult = 
                workflowStepBackService.validateStepBack("non-existent-task-id");
        
        assertThat(validationResult.isAllowed()).isFalse();
        assertThat(validationResult.getValidationErrors()).contains("Task not found: non-existent-task-id");
        assertThat(validationResult.getScenario()).isEqualTo(WorkflowStepBackService.StepBackScenario.UNKNOWN);
    }
}