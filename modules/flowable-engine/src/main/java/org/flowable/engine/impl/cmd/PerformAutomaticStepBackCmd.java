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
package org.flowable.engine.impl.cmd;

import java.util.Map;

import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.service.AutomaticStepBackService;

/**
 * Command for performing automatic step back operations.
 * 
 * @author Flowable Team
 */
public class PerformAutomaticStepBackCmd implements Command<String> {

    protected String currentTaskId;
    protected String processInstanceId;
    protected Map<String, Object> variables;

    public PerformAutomaticStepBackCmd(String currentTaskId, String processInstanceId, Map<String, Object> variables) {
        this.currentTaskId = currentTaskId;
        this.processInstanceId = processInstanceId;
        this.variables = variables;
    }

    @Override
    public String execute(CommandContext commandContext) {
        AutomaticStepBackService automaticStepBackService = new AutomaticStepBackService();
        return automaticStepBackService.performAutomaticStepBack(currentTaskId, processInstanceId, variables);
    }
}