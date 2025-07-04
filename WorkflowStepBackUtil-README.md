# WorkflowStepBackUtil - Flowable Workflow Step Back Utility

## Overview

The `WorkflowStepBackUtil` is a comprehensive utility class that provides thread-safe methods for handling workflow step back functionality in Flowable Engine. It supports different scenarios of workflow step back including sequential flows, parallel gateways, and subprocess interactions.

## Features

- **Sequential Flow Step Back**: Navigate backwards in simple sequential workflow processes
- **Parallel Gateway Step Back**: Handle step back operations in parallel gateway scenarios
- **Subprocess Step Back**: Support step back between main processes and sub-processes  
- **Thread Safety**: All operations are protected with read-write locks for concurrent access
- **Validation**: Comprehensive validation to ensure target steps are valid step back targets
- **Error Handling**: Robust error handling with meaningful error messages

## Core Functionality

### 1. Sequential Flow Operations

#### Get Previous Task
```java
HistoricTaskInstance previousTask = WorkflowStepBackUtil.getPreviousTaskInSequentialFlow(
    currentTaskId, processEngineConfiguration);
```

### 2. Parallel Gateway Operations

#### Handle Parallel Gateway Step Back
```java
WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleParallelGatewayStepBack(
    taskId, targetActivityId, processEngineConfiguration);
```

#### Step Back to Parallel Gateway Branches
```java
List<String> targetBranchActivityIds = Arrays.asList("taskA", "taskB");
WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleStepBackToParallelGatewayBranches(
    taskId, targetBranchActivityIds, processEngineConfiguration);
```

### 3. Subprocess Operations

#### Handle Subprocess Step Back
```java
WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleSubProcessStepBack(
    taskId, targetActivityId, processEngineConfiguration);
```

### 4. Utility Methods

#### Determine Process Type
```java
WorkflowStepBackUtil.ProcessType processType = WorkflowStepBackUtil.determineProcessType(
    processInstanceId, activityId, processEngineConfiguration);
```

#### Validate Step Back Target
```java
boolean isValid = WorkflowStepBackUtil.isValidStepBackTarget(
    processInstanceId, currentActivityId, targetActivityId, processEngineConfiguration);
```

#### Get Task Execution Information
```java
Map<String, Object> taskInfo = WorkflowStepBackUtil.getTaskExecutionInfo(
    taskId, processEngineConfiguration);
```

## Data Structures

### ProcessType Enumeration
- `SEQUENTIAL`: Simple sequential flow process
- `PARALLEL_GATEWAY`: Process involving parallel gateways
- `SUBPROCESS`: Process involving subprocesses
- `EVENT_SUBPROCESS`: Process involving event subprocesses

### StepBackResult Class
Contains the result of step back operations:
- `success`: Whether the operation succeeded
- `message`: Descriptive message about the operation
- `movedExecutionIds`: List of execution IDs that were moved
- `targetActivityId`: The target activity ID

## Thread Safety

The utility class uses `ReentrantReadWriteLock` to ensure thread safety:
- **Read locks** for operations that only read data (queries, validations)
- **Write locks** for operations that modify process state (step back operations)

This allows multiple concurrent read operations while ensuring exclusive access for write operations.

## Error Handling

The utility provides comprehensive error handling:
- **FlowableIllegalArgumentException**: For invalid input parameters
- **FlowableException**: For process-related errors
- **Validation**: Ensures target activities are valid step back targets

## Usage Examples

### Example 1: Simple Sequential Step Back
```java
// Get current task
Task currentTask = taskService.createTaskQuery().taskId(taskId).singleResult();

// Find previous task
HistoricTaskInstance previousTask = WorkflowStepBackUtil.getPreviousTaskInSequentialFlow(
    currentTask.getId(), processEngineConfiguration);

if (previousTask != null) {
    System.out.println("Previous task: " + previousTask.getName());
}
```

### Example 2: Parallel Gateway Step Back
```java
// Validate step back target
boolean isValid = WorkflowStepBackUtil.isValidStepBackTarget(
    processInstanceId, currentActivityId, targetActivityId, processEngineConfiguration);

if (isValid) {
    // Perform step back
    WorkflowStepBackUtil.StepBackResult result = WorkflowStepBackUtil.handleParallelGatewayStepBack(
        taskId, targetActivityId, processEngineConfiguration);
    
    if (result.isSuccess()) {
        System.out.println("Step back successful: " + result.getMessage());
        System.out.println("Moved executions: " + result.getMovedExecutionIds().size());
    }
}
```

### Example 3: Process Type Detection
```java
WorkflowStepBackUtil.ProcessType processType = WorkflowStepBackUtil.determineProcessType(
    processInstanceId, activityId, processEngineConfiguration);

switch (processType) {
    case SEQUENTIAL:
        // Handle sequential flow
        break;
    case PARALLEL_GATEWAY:
        // Handle parallel gateway
        break;
    case SUBPROCESS:
        // Handle subprocess
        break;
}
```

## Dependencies

The utility requires:
- Flowable Engine (RuntimeService, TaskService, HistoryService)
- ProcessEngineConfiguration
- Access to process definitions and runtime data

## Testing

Comprehensive test suite includes:
- Unit tests for all major functionality
- Integration tests with actual BPMN processes
- Thread safety tests
- Error condition tests
- Edge case validation

## Implementation Notes

1. **Command Context**: Some operations require execution within Flowable's command context
2. **Process State**: Step back operations may not always succeed due to process state constraints
3. **Validation**: Always validate step back targets before attempting operations
4. **Error Recovery**: Handle exceptions gracefully as step back operations may fail in certain scenarios

## Performance Considerations

- Operations are optimized for typical workflow scenarios
- Thread safety mechanisms ensure safe concurrent access
- Database queries are minimized through efficient data retrieval
- Process definition caching is leveraged where possible

## Best Practices

1. **Always validate** step back targets before attempting operations
2. **Handle exceptions** gracefully as step back may not always be possible
3. **Use within transactions** for consistent state management
4. **Test thoroughly** in your specific process scenarios
5. **Monitor performance** in high-concurrency environments

This utility provides a robust foundation for implementing workflow step back functionality in Flowable-based applications while maintaining thread safety and proper error handling.