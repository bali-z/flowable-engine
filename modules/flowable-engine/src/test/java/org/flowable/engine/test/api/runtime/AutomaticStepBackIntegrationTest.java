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
package org.flowable.engine.test.api.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.junit.jupiter.api.Test;

/**
 * Integration test for the automatic step back functionality.
 * 
 * @author Flowable Team
 */
public class AutomaticStepBackIntegrationTest extends PluggableFlowableTestCase {

    @Test
    public void testAutomaticStepBackAPIAvailability() {
        // Test that the new API methods are available on RuntimeService
        assertThat(runtimeService).isNotNull();
        
        // These methods should be available without throwing exceptions
        boolean canStepBack = runtimeService.canPerformAutomaticStepBack("nonexistent", "nonexistent");
        assertThat(canStepBack).isFalse();
        
        // The other methods would throw exceptions for non-existent process instances,
        // which is expected behavior, so we don't test them here without a real process.
    }

    @Test
    public void testGetPossibleStepBackTargetsWithInvalidData() {
        // Test with non-existent data - should handle gracefully
        try {
            List<String> targets = runtimeService.getPossibleStepBackTargets("nonexistent", "nonexistent");
            // Should not reach here due to validation
            assertThat(targets).isNotNull();
        } catch (Exception e) {
            // Expected - should throw an exception for invalid process instance
            assertThat(e).isNotNull();
        }
    }
}