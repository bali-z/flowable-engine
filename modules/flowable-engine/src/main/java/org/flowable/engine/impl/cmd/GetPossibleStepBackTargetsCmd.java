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

import java.util.List;

import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.engine.impl.service.AutomaticStepBackService;

/**
 * Command for getting possible step back targets.
 * 
 * @author Flowable Team
 */
public class GetPossibleStepBackTargetsCmd implements Command<List<String>> {

    protected String currentTaskId;
    protected String processInstanceId;

    public GetPossibleStepBackTargetsCmd(String currentTaskId, String processInstanceId) {
        this.currentTaskId = currentTaskId;
        this.processInstanceId = processInstanceId;
    }

    @Override
    public List<String> execute(CommandContext commandContext) {
        AutomaticStepBackService automaticStepBackService = new AutomaticStepBackService();
        return automaticStepBackService.getPossibleStepBackTargets(currentTaskId, processInstanceId);
    }
}