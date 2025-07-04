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
package org.flowable.engine.test.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.impl.util.WorkflowStepBackUtil;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

/**
 * Demonstration class showing how to use WorkflowStepBackUtil.
 * 
 * This class provides practical examples of how to use the workflow step back
 * functionality in different scenarios.
 *
 * @author Flowable Team
 */
public class WorkflowStepBackUtilDemo extends PluggableFlowableTestCase {

    /**
     * Demonstrates basic sequential flow step back functionality.
     */
    @Deployment(resources = { "org/flowable/engine/test/util/WorkflowStepBackUtilTest.sequentialFlow.bpmn20.xml" })
    public void demoSequentialFlowStepBack() {
        System.out.println("=== Sequential Flow Step Back Demo ===");
        
        // Start a process instance
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("sequentialFlow");
        System.out.println("Started process instance: " + processInstance.getId());
        
        // Complete first task
        Task task1 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        System.out.println("Current task: " + task1.getName() + " (ID: " + task1.getId() + ")");
        taskService.complete(task1.getId());
        
        // Get second task
        Task task2 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        System.out.println("Current task: " + task2.getName() + " (ID: " + task2.getId() + ")");
        
        // Get previous task information
        HistoricTaskInstance previousTask = WorkflowStepBackUtil.getPreviousTaskInSequentialFlow(
            task2.getId(), processEngineConfiguration);
        
        if (previousTask != null) {
            System.out.println("Previous task found: " + previousTask.getName() + " (ID: " + previousTask.getId() + ")");
            System.out.println("Previous task completed at: " + previousTask.getEndTime());
        } else {
            System.out.println("No previous task found");
        }
        
        // Get task execution information
        Map<String, Object> taskInfo = WorkflowStepBackUtil.getTaskExecutionInfo(task2.getId(), processEngineConfiguration);
        System.out.println("Task execution info:");
        System.out.println("  - Process Instance ID: " + taskInfo.get("processInstanceId"));
        System.out.println("  - Execution ID: " + taskInfo.get("executionId"));
        System.out.println("  - Task Definition Key: " + taskInfo.get("taskDefinitionKey"));
        System.out.println("  - Process Type: " + taskInfo.get("processType"));
        
        System.out.println("=== Sequential Flow Demo Complete ===\n");
    }

    /**
     * Demonstrates parallel gateway step back functionality.
     */
    @Deployment(resources = { "org/flowable/engine/test/util/WorkflowStepBackUtilTest.parallelGateway.bpmn20.xml" })
    public void demoParallelGatewayStepBack() {
        System.out.println("=== Parallel Gateway Step Back Demo ===");
        
        // Start a process instance
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
        System.out.println("Started process instance: " + processInstance.getId());
        
        // Complete first task to reach parallel gateway
        Task task1 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        System.out.println("Completing task: " + task1.getName());
        taskService.complete(task1.getId());
        
        // Get parallel tasks
        List<Task> parallelTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .orderByTaskName().asc()
            .list();
        
        System.out.println("Parallel tasks found: " + parallelTasks.size());
        for (Task task : parallelTasks) {
            System.out.println("  - " + task.getName() + " (ID: " + task.getId() + ")");
        }
        
        // Demonstrate step back from parallel gateway
        if (!parallelTasks.isEmpty()) {
            Task firstParallelTask = parallelTasks.get(0);
            
            // Check if target is a valid step back target
            boolean isValidTarget = WorkflowStepBackUtil.isValidStepBackTarget(
                processInstance.getId(), 
                firstParallelTask.getTaskDefinitionKey(), 
                task1.getTaskDefinitionKey(), 
                processEngineConfiguration);
            
            System.out.println("Is '" + task1.getTaskDefinitionKey() + "' a valid step back target? " + isValidTarget);
            
            // Attempt step back (this may not always succeed in demo due to process constraints)
            try {
                WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleParallelGatewayStepBack(
                    firstParallelTask.getId(), task1.getTaskDefinitionKey(), processEngineConfiguration);
                
                System.out.println("Step back result: " + result.isSuccess());
                System.out.println("Message: " + result.getMessage());
                System.out.println("Moved executions: " + result.getMovedExecutionIds().size());
            } catch (Exception e) {
                System.out.println("Step back failed (expected in demo): " + e.getMessage());
            }
        }
        
        System.out.println("=== Parallel Gateway Demo Complete ===\n");
    }

    /**
     * Demonstrates subprocess step back functionality.
     */
    @Deployment(resources = { "org/flowable/engine/test/util/WorkflowStepBackUtilTest.subprocess.bpmn20.xml" })
    public void demoSubProcessStepBack() {
        System.out.println("=== Subprocess Step Back Demo ===");
        
        // Start a process instance
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("subProcessFlow");
        System.out.println("Started process instance: " + processInstance.getId());
        
        // Complete first task to enter subprocess
        Task taskBeforeSub = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        System.out.println("Completing task: " + taskBeforeSub.getName());
        taskService.complete(taskBeforeSub.getId());
        
        // Get task in subprocess
        Task subprocessTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        System.out.println("Current task in subprocess: " + subprocessTask.getName() + " (ID: " + subprocessTask.getId() + ")");
        
        // Determine process types
        try {
            WorkflowStepBackUtil.ProcessType currentProcessType = WorkflowStepBackUtil.determineProcessType(
                processInstance.getId(), subprocessTask.getTaskDefinitionKey(), processEngineConfiguration);
            System.out.println("Current process type: " + currentProcessType);
            
            WorkflowStepBackUtil.ProcessType targetProcessType = WorkflowStepBackUtil.determineProcessType(
                processInstance.getId(), taskBeforeSub.getTaskDefinitionKey(), processEngineConfiguration);
            System.out.println("Target process type: " + targetProcessType);
            
            // Attempt step back from subprocess (this may not always succeed in demo)
            WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleSubProcessStepBack(
                subprocessTask.getId(), taskBeforeSub.getTaskDefinitionKey(), processEngineConfiguration);
            
            System.out.println("Step back result: " + result.isSuccess());
            System.out.println("Message: " + result.getMessage());
            if (result.isSuccess()) {
                System.out.println("Moved executions: " + result.getMovedExecutionIds().size());
            }
        } catch (Exception e) {
            System.out.println("Step back failed (expected in demo): " + e.getMessage());
        }
        
        System.out.println("=== Subprocess Demo Complete ===\n");
    }

    /**
     * Demonstrates step back to parallel gateway branches.
     */
    @Deployment(resources = { "org/flowable/engine/test/util/WorkflowStepBackUtilTest.parallelGateway.bpmn20.xml" })
    public void demoStepBackToParallelBranches() {
        System.out.println("=== Step Back to Parallel Branches Demo ===");
        
        // Start a process instance and complete it to the end
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
        System.out.println("Started process instance: " + processInstance.getId());
        
        // Complete first task
        Task task1 = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        taskService.complete(task1.getId());
        
        // Complete parallel tasks
        List<Task> parallelTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .list();
        
        System.out.println("Completing " + parallelTasks.size() + " parallel tasks");
        for (Task task : parallelTasks) {
            System.out.println("  Completing: " + task.getName());
            taskService.complete(task.getId());
        }
        
        // Get final task
        Task finalTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
        if (finalTask != null) {
            System.out.println("Final task: " + finalTask.getName());
            
            // Demonstrate step back to multiple parallel branches
            List<String> targetBranchActivityIds = Arrays.asList("taskA", "taskB");
            System.out.println("Attempting step back to branches: " + targetBranchActivityIds);
            
            try {
                WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleStepBackToParallelGatewayBranches(
                    finalTask.getId(), targetBranchActivityIds, processEngineConfiguration);
                
                System.out.println("Step back result: " + result.isSuccess());
                System.out.println("Message: " + result.getMessage());
                if (result.isSuccess()) {
                    System.out.println("Created " + result.getMovedExecutionIds().size() + " branch executions");
                }
            } catch (Exception e) {
                System.out.println("Step back failed (expected in demo): " + e.getMessage());
            }
        }
        
        System.out.println("=== Parallel Branches Demo Complete ===\n");
    }

    /**
     * Demonstrates thread safety of the utility.
     */
    public void demoThreadSafety() {
        System.out.println("=== Thread Safety Demo ===");
        
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        System.out.println("Starting " + threadCount + " concurrent threads...");
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // Test thread-safe method calls
                    Map<String, Object> info = WorkflowStepBackUtil.getTaskExecutionInfo("nonExistent", processEngineConfiguration);
                    results[index] = info.isEmpty(); // Should always be empty for non-existent task
                    System.out.println("Thread " + index + " completed successfully");
                } catch (Exception e) {
                    System.out.println("Thread " + index + " failed: " + e.getMessage());
                    results[index] = false;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            System.out.println("Thread interruption: " + e.getMessage());
        }
        
        // Check results
        boolean allSuccessful = true;
        for (boolean result : results) {
            if (!result) {
                allSuccessful = false;
                break;
            }
        }
        
        System.out.println("All threads completed successfully: " + allSuccessful);
        System.out.println("=== Thread Safety Demo Complete ===\n");
    }

    /**
     * Runs all demonstrations.
     */
    public void runAllDemos() {
        System.out.println("========================================");
        System.out.println("WorkflowStepBackUtil Demonstration");
        System.out.println("========================================\n");
        
        try {
            demoSequentialFlowStepBack();
            demoParallelGatewayStepBack();
            demoSubProcessStepBack();
            demoStepBackToParallelBranches();
            demoThreadSafety();
        } catch (Exception e) {
            System.out.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("========================================");
        System.out.println("All Demonstrations Complete");
        System.out.println("========================================");
    }
}