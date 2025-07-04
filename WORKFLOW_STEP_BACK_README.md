# Workflow Step Back Functionality

## Overview / 概述

This implementation provides comprehensive workflow step back functionality for the Flowable engine, allowing users to revert tasks to previous activities in a workflow process.

此实现为Flowable引擎提供全面的工作流回退功能，允许用户将任务回退到工作流过程中的先前活动。

## Features / 功能特性

### Core Features / 核心功能
- **Basic Step Back** / **基本回退**: Step back to the immediate previous activity
- **Targeted Step Back** / **定向回退**: Step back to any specified previous activity  
- **Variable Step Back** / **带变量回退**: Step back while modifying process variables
- **Batch Step Back** / **批量回退**: Step back multiple tasks simultaneously
- **Multi-instance Support** / **多实例支持**: Handle multi-instance task step back scenarios
- **Subprocess Support** / **子流程支持**: Handle subprocess task step back scenarios

### Technical Features / 技术特性
- **Thread Safety** / **线程安全**: Uses ReentrantReadWriteLock for concurrent operations
- **Comprehensive Error Handling** / **全面错误处理**: Custom exception types with detailed error codes
- **Audit Logging** / **审计日志**: Detailed logging for all step back operations
- **Validation** / **验证**: Pre-operation validation to ensure step back feasibility
- **Bilingual Documentation** / **双语文档**: Support for English and Chinese

## Components / 组件

### 1. WorkflowStepBackService / 工作流回退服务

The main service class providing step back functionality.

提供回退功能的主要服务类。

```java
// Create service instance
WorkflowStepBackService stepBackService = new WorkflowStepBackService(processEngineConfiguration);

// Basic step back
stepBackService.stepBackTask(taskId, userId, reason);

// Step back to specific activity
stepBackService.stepBackToActivity(taskId, targetActivityId, userId, reason);

// Step back with variables
Map<String, Object> variables = new HashMap<>();
variables.put("key", "value");
stepBackService.stepBackWithVariables(taskId, targetActivityId, userId, reason, variables, null);
```

### 2. WorkflowStepBackException / 工作流回退异常

Custom exception class for step back operations with detailed error codes.

带有详细错误代码的回退操作自定义异常类。

```java
// Error codes available:
// INVALID_TASK_STATE - Invalid task state for step back
// PERMISSION_DENIED - Permission denied for step back
// PROCESS_CONFIGURATION_ERROR - Process configuration error
// INVALID_TARGET_NODE - Invalid target node for step back
// MULTIPLE_INSTANCE_ERROR - Multi-instance operation error
// SUBPROCESS_ERROR - Subprocess operation error
// EXECUTION_NOT_FOUND - Execution not found
// TASK_NOT_FOUND - Task not found
// UNSUPPORTED_PROCESS_TYPE - Unsupported process type
```

### 3. WorkflowStepBackUtils / 工作流回退工具类

Utility class providing helper methods for step back operations.

为回退操作提供辅助方法的工具类。

```java
// Process type determination
WorkflowStepBackUtils.ProcessType processType = 
    WorkflowStepBackUtils.determineProcessType(task, execution);

// Permission checking
boolean hasPermission = WorkflowStepBackUtils.hasStepBackPermission(task, userId);

// Find possible step back targets
List<String> targets = WorkflowStepBackUtils.findPossibleStepBackTargets(
    processDefinition, currentActivityId);

// Validation methods
WorkflowStepBackUtils.validateTaskForStepBack(task);
WorkflowStepBackUtils.validateExecutionForStepBack(execution);
WorkflowStepBackUtils.validateTargetActivity(processDefinition, targetActivityId);
```

## Usage Examples / 使用示例

### Basic Step Back / 基本回退

```java
@Autowired
private ProcessEngineConfiguration processEngineConfiguration;

public void performStepBack(String taskId, String userId) {
    WorkflowStepBackService stepBackService = 
        new WorkflowStepBackService(processEngineConfiguration);
    
    try {
        // Validate step back is possible
        WorkflowStepBackService.StepBackValidationResult validation = 
            stepBackService.validateStepBack(taskId, null, userId);
        
        if (validation.isValid()) {
            stepBackService.stepBackTask(taskId, userId, "User requested step back");
        } else {
            throw new IllegalStateException("Step back not allowed: " + validation.getMessage());
        }
    } catch (WorkflowStepBackException e) {
        // Handle step back specific errors
        System.err.println("Step back failed: " + e.getErrorCode() + " - " + e.getMessage());
    }
}
```

### Advanced Step Back with Variables / 带变量的高级回退

```java
public void performAdvancedStepBack(String taskId, String targetActivityId, String userId) {
    WorkflowStepBackService stepBackService = 
        new WorkflowStepBackService(processEngineConfiguration);
    
    // Prepare variables to modify during step back
    Map<String, Object> processVariables = new HashMap<>();
    processVariables.put("correctionReason", "Data correction required");
    processVariables.put("correctionTimestamp", new Date());
    
    Map<String, Object> localVariables = new HashMap<>();
    localVariables.put("stepBackPerformed", true);
    
    try {
        stepBackService.stepBackWithVariables(
            taskId, 
            targetActivityId, 
            userId, 
            "Data correction step back",
            processVariables,
            localVariables
        );
        
        System.out.println("Step back completed successfully with variable modifications");
    } catch (WorkflowStepBackException e) {
        System.err.println("Step back failed: " + e.toString());
    }
}
```

### Batch Step Back / 批量回退

```java
public void performBatchStepBack(List<String> taskIds, String userId) {
    WorkflowStepBackService stepBackService = 
        new WorkflowStepBackService(processEngineConfiguration);
    
    try {
        stepBackService.batchStepBack(taskIds, null, userId, "Batch correction");
        System.out.println("Batch step back completed for " + taskIds.size() + " tasks");
    } catch (WorkflowStepBackException e) {
        System.err.println("Batch step back failed: " + e.getMessage());
    }
}
```

## Error Handling / 错误处理

The step back functionality provides comprehensive error handling through the `WorkflowStepBackException` class:

回退功能通过`WorkflowStepBackException`类提供全面的错误处理：

```java
try {
    stepBackService.stepBackTask(taskId, userId, reason);
} catch (WorkflowStepBackException e) {
    switch (e.getErrorCode()) {
        case TASK_NOT_FOUND:
            // Handle task not found
            break;
        case PERMISSION_DENIED:
            // Handle permission issues
            break;
        case INVALID_TASK_STATE:
            // Handle invalid task state
            break;
        // ... handle other error codes
    }
    
    // Access context information
    String taskId = e.getTaskId();
    String processInstanceId = e.getProcessInstanceId();
    String targetActivityId = e.getTargetActivityId();
}
```

## Validation / 验证

Before performing step back operations, use validation methods to ensure the operation is feasible:

在执行回退操作之前，使用验证方法确保操作可行：

```java
// Validate step back possibility
WorkflowStepBackService.StepBackValidationResult result = 
    stepBackService.validateStepBack(taskId, targetActivityId, userId);

if (result.isValid()) {
    // Proceed with step back
    stepBackService.stepBackToActivity(taskId, targetActivityId, userId, reason);
} else {
    // Handle validation failure
    System.err.println("Validation failed: " + result.getMessage());
    System.err.println("Chinese message: " + result.getMessageZh());
    
    // Get alternative targets
    List<String> possibleTargets = result.getPossibleTargets();
    System.out.println("Possible targets: " + possibleTargets);
}
```

## Supported Process Types / 支持的流程类型

- **BPMN Processes** / **BPMN流程**: Fully supported
- **CMMN Cases** / **CMMN案例**: Requires CMMN-specific handling  
- **DMN Decisions** / **DMN决策**: Not supported (decision tables are not reversible)

## Threading and Concurrency / 线程和并发

The `WorkflowStepBackService` is thread-safe and uses `ReentrantReadWriteLock` to handle concurrent operations:

`WorkflowStepBackService`是线程安全的，使用`ReentrantReadWriteLock`处理并发操作：

- **Read operations** (validation, querying): Multiple threads can execute simultaneously
- **Write operations** (actual step back): Exclusive access to ensure consistency

## Best Practices / 最佳实践

1. **Always validate** before performing step back operations / **始终验证**回退操作
2. **Handle exceptions** appropriately based on error codes / 根据错误代码**适当处理异常**
3. **Use meaningful reasons** for audit trails / 为审计跟踪**使用有意义的原因**
4. **Check permissions** before allowing users to step back / 在允许用户回退之前**检查权限**
5. **Consider process impact** when stepping back with variables / 在带变量回退时**考虑流程影响**

## Testing / 测试

The implementation includes comprehensive tests in `WorkflowStepBackTest.java`:

实现包括`WorkflowStepBackTest.java`中的综合测试：

```bash
# Run all step back tests
mvn test -pl modules/flowable-engine -Dtest=WorkflowStepBackTest

# Run specific test
mvn test -pl modules/flowable-engine -Dtest=WorkflowStepBackTest#testBasicStepBack
```

## Integration / 集成

To integrate the step back functionality into your Flowable application:

要将回退功能集成到您的Flowable应用程序中：

1. Create an instance of `WorkflowStepBackService` with your `ProcessEngineConfiguration`
2. Use the service methods to perform step back operations
3. Handle `WorkflowStepBackException` appropriately
4. Implement proper authorization checks using the utility methods

## License / 许可证

This implementation follows the same Apache License 2.0 as the Flowable engine.

此实现遵循与Flowable引擎相同的Apache License 2.0许可证。