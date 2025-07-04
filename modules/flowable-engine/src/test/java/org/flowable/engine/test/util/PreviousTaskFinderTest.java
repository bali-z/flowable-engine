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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.impl.util.PreviousTaskFinder;
import org.junit.jupiter.api.Test;

/**
 * Test cases for the PreviousTaskFinder utility.
 * 
 * @author Flowable Team
 */
public class PreviousTaskFinderTest extends PluggableFlowableTestCase {

    @Test
    public void testFindPreviousTasksWithNullTaskId() {
        assertThatThrownBy(() -> PreviousTaskFinder.findPreviousTasks(null, null))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Current task ID and execution entity are required");
    }

    @Test
    public void testFindPreviousTasksWithNullExecution() {
        assertThatThrownBy(() -> PreviousTaskFinder.findPreviousTasks("taskId", null))
                .isInstanceOf(FlowableException.class)
                .hasMessageContaining("Current task ID and execution entity are required");
    }

    @Test
    public void testFindMostRecentPreviousTaskWithNullList() {
        String result = PreviousTaskFinder.findMostRecentPreviousTask(null, null);
        assertThat(result).isNull();
    }

    @Test
    public void testFindMostRecentPreviousTaskWithEmptyList() {
        List<String> emptyList = List.of();
        String result = PreviousTaskFinder.findMostRecentPreviousTask(emptyList, null);
        assertThat(result).isNull();
    }

    @Test
    public void testFindMostRecentPreviousTaskWithSingleTask() {
        List<String> singleTask = List.of("task1");
        String result = PreviousTaskFinder.findMostRecentPreviousTask(singleTask, null);
        assertThat(result).isEqualTo("task1");
    }

    @Test
    public void testFindMostRecentPreviousTaskWithMultipleTasks() {
        List<String> multipleTasks = List.of("task1", "task2", "task3");
        String result = PreviousTaskFinder.findMostRecentPreviousTask(multipleTasks, null);
        // Should return the first task as the default implementation
        assertThat(result).isEqualTo("task1");
    }
}