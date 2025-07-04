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
package org.flowable.engine.test.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.impl.service.AutomaticStepBackService;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

/**
 * Test cases for the AutomaticStepBackService functionality.
 * 
 * @author Flowable Team
 */
public class AutomaticStepBackServiceTest extends PluggableFlowableTestCase {

    @Test
    public void testCanPerformAutomaticStepBackWithNullParameters() {
        AutomaticStepBackService service = new AutomaticStepBackService();
        
        // Test with null task ID
        assertThat(service.canPerformAutomaticStepBack(null, "processInstance")).isFalse();
        
        // Test with null process instance ID
        assertThat(service.canPerformAutomaticStepBack("taskId", null)).isFalse();
        
        // Test with both null
        assertThat(service.canPerformAutomaticStepBack(null, null)).isFalse();
    }

    @Test
    public void testPerformAutomaticStepBackWithNullParameters() {
        AutomaticStepBackService service = new AutomaticStepBackService();
        
        // Test with null task ID
        assertThatThrownBy(() -> service.performAutomaticStepBack(null, "processInstance"))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Current task ID and process instance ID are required");
        
        // Test with null process instance ID
        assertThatThrownBy(() -> service.performAutomaticStepBack("taskId", null))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Current task ID and process instance ID are required");
    }

    @Test
    public void testGetPossibleStepBackTargetsWithNullParameters() {
        AutomaticStepBackService service = new AutomaticStepBackService();
        
        // Test with null task ID
        assertThatThrownBy(() -> service.getPossibleStepBackTargets(null, "processInstance"))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Current task ID and process instance ID are required");
        
        // Test with null process instance ID
        assertThatThrownBy(() -> service.getPossibleStepBackTargets("taskId", null))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Current task ID and process instance ID are required");
    }

    @Test
    public void testCanPerformAutomaticStepBackWithInvalidProcessInstance() {
        AutomaticStepBackService service = new AutomaticStepBackService();
        
        // Test with non-existent process instance
        boolean canStepBack = service.canPerformAutomaticStepBack("taskId", "nonexistent");
        assertThat(canStepBack).isFalse();
    }

    @Test
    public void testGetPossibleStepBackTargetsWithInvalidProcessInstance() {
        AutomaticStepBackService service = new AutomaticStepBackService();
        
        // Test with non-existent process instance
        assertThatThrownBy(() -> service.getPossibleStepBackTargets("taskId", "nonexistent"))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Failed to get step back targets");
    }

    @Test
    public void testPerformAutomaticStepBackWithInvalidProcessInstance() {
        AutomaticStepBackService service = new AutomaticStepBackService();
        
        // Test with non-existent process instance
        assertThatThrownBy(() -> service.performAutomaticStepBack("taskId", "nonexistent"))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Automatic step back operation failed");
    }

    @Test
    public void testAutomaticStepBackServiceInstantiation() {
        // Test default constructor
        AutomaticStepBackService service1 = new AutomaticStepBackService();
        assertThat(service1).isNotNull();
        
        // Test constructor with WorkflowStepBackService
        AutomaticStepBackService service2 = new AutomaticStepBackService(null);
        assertThat(service2).isNotNull();
    }
}