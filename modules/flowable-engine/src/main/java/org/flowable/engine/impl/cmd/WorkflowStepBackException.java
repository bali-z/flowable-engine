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

import org.flowable.common.engine.api.FlowableException;

/**
 * Custom exception for workflow step back operations.
 * 工作流回退操作的自定义异常类。
 * 
 * This exception is thrown when errors occur during workflow step back operations,
 * such as invalid task states, permission issues, or process configuration problems.
 * 当工作流回退操作中发生错误时抛出此异常，例如无效的任务状态、权限问题或流程配置问题。
 * 
 * @author Flowable Engine Team
 */
public class WorkflowStepBackException extends FlowableException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for different types of step back failures
     * 不同类型回退失败的错误代码
     */
    public enum StepBackErrorCode {
        INVALID_TASK_STATE("invalid.task.state", "Invalid task state for step back operation"),
        PERMISSION_DENIED("permission.denied", "Permission denied for step back operation"),
        PROCESS_CONFIGURATION_ERROR("process.config.error", "Process configuration does not support step back"),
        INVALID_TARGET_NODE("invalid.target.node", "Invalid target node for step back"),
        MULTIPLE_INSTANCE_ERROR("multi.instance.error", "Error in multi-instance step back operation"),
        SUBPROCESS_ERROR("subprocess.error", "Error in subprocess step back operation"),
        EXECUTION_NOT_FOUND("execution.not.found", "Execution not found for step back operation"),
        TASK_NOT_FOUND("task.not.found", "Task not found for step back operation"),
        UNSUPPORTED_PROCESS_TYPE("unsupported.process.type", "Unsupported process type for step back");

        private final String code;
        private final String description;

        StepBackErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    private final StepBackErrorCode errorCode;
    private final String taskId;
    private final String processInstanceId;
    private final String targetActivityId;

    /**
     * Constructor with error code and message.
     * 使用错误代码和消息的构造函数。
     * 
     * @param errorCode The specific error code
     * @param message The error message
     */
    public WorkflowStepBackException(StepBackErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.taskId = null;
        this.processInstanceId = null;
        this.targetActivityId = null;
    }

    /**
     * Constructor with error code, message and cause.
     * 使用错误代码、消息和原因的构造函数。
     * 
     * @param errorCode The specific error code
     * @param message The error message
     * @param cause The underlying cause
     */
    public WorkflowStepBackException(StepBackErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.taskId = null;
        this.processInstanceId = null;
        this.targetActivityId = null;
    }

    /**
     * Constructor with detailed context information.
     * 使用详细上下文信息的构造函数。
     * 
     * @param errorCode The specific error code
     * @param message The error message
     * @param taskId The task ID involved in the operation
     * @param processInstanceId The process instance ID
     * @param targetActivityId The target activity ID for step back
     */
    public WorkflowStepBackException(StepBackErrorCode errorCode, String message, 
                                   String taskId, String processInstanceId, String targetActivityId) {
        super(message);
        this.errorCode = errorCode;
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.targetActivityId = targetActivityId;
    }

    /**
     * Constructor with detailed context information and cause.
     * 使用详细上下文信息和原因的构造函数。
     * 
     * @param errorCode The specific error code
     * @param message The error message
     * @param taskId The task ID involved in the operation
     * @param processInstanceId The process instance ID
     * @param targetActivityId The target activity ID for step back
     * @param cause The underlying cause
     */
    public WorkflowStepBackException(StepBackErrorCode errorCode, String message, 
                                   String taskId, String processInstanceId, String targetActivityId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.taskId = taskId;
        this.processInstanceId = processInstanceId;
        this.targetActivityId = targetActivityId;
    }

    /**
     * Gets the error code.
     * 获取错误代码。
     * 
     * @return The error code
     */
    public StepBackErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the task ID.
     * 获取任务ID。
     * 
     * @return The task ID, may be null
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Gets the process instance ID.
     * 获取流程实例ID。
     * 
     * @return The process instance ID, may be null
     */
    public String getProcessInstanceId() {
        return processInstanceId;
    }

    /**
     * Gets the target activity ID.
     * 获取目标活动ID。
     * 
     * @return The target activity ID, may be null
     */
    public String getTargetActivityId() {
        return targetActivityId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WorkflowStepBackException{");
        sb.append("errorCode=").append(errorCode);
        if (taskId != null) {
            sb.append(", taskId='").append(taskId).append('\'');
        }
        if (processInstanceId != null) {
            sb.append(", processInstanceId='").append(processInstanceId).append('\'');
        }
        if (targetActivityId != null) {
            sb.append(", targetActivityId='").append(targetActivityId).append('\'');
        }
        sb.append(", message='").append(getMessage()).append('\'');
        sb.append('}');
        return sb.toString();
    }
}