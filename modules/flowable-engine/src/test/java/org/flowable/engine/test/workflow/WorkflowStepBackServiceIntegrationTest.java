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

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngines;
import org.flowable.engine.WorkflowStepBackService;
import org.junit.jupiter.api.Test;

/**
 * Simple integration test to verify WorkflowStepBackService is properly integrated.
 * 
 * @author Flowable Team
 */
public class WorkflowStepBackServiceIntegrationTest {

    @Test
    public void testServiceIntegration() {
        // Get the default process engine
        ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
        assertThat(processEngine).isNotNull();
        
        // Verify that the WorkflowStepBackService is available
        WorkflowStepBackService workflowStepBackService = processEngine.getWorkflowStepBackService();
        assertThat(workflowStepBackService).isNotNull();
        
        // Test basic validation with null input
        WorkflowStepBackService.StepBackValidationResult result = workflowStepBackService.validateStepBack(null);
        assertThat(result).isNotNull();
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getValidationErrors()).contains("Current task ID cannot be null");
        assertThat(result.getScenario()).isEqualTo(WorkflowStepBackService.StepBackScenario.UNKNOWN);
        
        // Test basic validation with non-existent task
        result = workflowStepBackService.validateStepBack("non-existent-task-id");
        assertThat(result).isNotNull();
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getValidationErrors()).contains("Task not found: non-existent-task-id");
        assertThat(result.getScenario()).isEqualTo(WorkflowStepBackService.StepBackScenario.UNKNOWN);
        
        System.out.println("WorkflowStepBackService integration test passed successfully!");
    }
}