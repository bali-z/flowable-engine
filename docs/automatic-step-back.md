# Automatic Step Back Functionality

This document describes the new automatic step back functionality implemented in Flowable Engine.

## Overview

The automatic step back functionality provides a simple way to move workflow execution back to previous tasks without requiring manual specification of the target task. The system automatically analyzes the workflow structure and determines the most appropriate previous task to step back to.

## Key Components

### 1. PreviousTaskFinder
- **Location**: `org.flowable.engine.impl.util.PreviousTaskFinder`
- **Purpose**: Analyzes BPMN models to find previous user tasks
- **Features**:
  - Handles parallel gateway scenarios
  - Supports subprocess navigation
  - Recursive workflow traversal
  - Comprehensive error handling

### 2. WorkflowStepBackService  
- **Location**: `org.flowable.engine.impl.service.WorkflowStepBackService`
- **Purpose**: Executes step back operations with different strategies
- **Features**:
  - Strategy-based execution (Simple, Parallel Gateway, Subprocess)
  - Validation of step back operations
  - Integration with existing ChangeActivityState infrastructure

### 3. AutomaticStepBackService
- **Location**: `org.flowable.engine.impl.service.AutomaticStepBackService`
- **Purpose**: Main orchestration service combining the above components
- **Features**:
  - Automatic target task determination
  - Comprehensive error handling
  - Support for process variables during step back

## RuntimeService API

The functionality is exposed through new methods in the `RuntimeService`:

### Methods

```java
// Perform automatic step back
String performAutomaticStepBack(String currentTaskId, String processInstanceId);
String performAutomaticStepBack(String currentTaskId, String processInstanceId, Map<String, Object> variables);

// Check if step back is possible
boolean canPerformAutomaticStepBack(String currentTaskId, String processInstanceId);

// Get possible step back targets
List<String> getPossibleStepBackTargets(String currentTaskId, String processInstanceId);
```

### Usage Examples

```java
// Basic automatic step back
String targetTaskId = runtimeService.performAutomaticStepBack("currentTask", "processInstanceId");

// Step back with variables
Map<String, Object> variables = new HashMap<>();
variables.put("reason", "Data correction needed");
String targetTaskId = runtimeService.performAutomaticStepBack("currentTask", "processInstanceId", variables);

// Check if step back is possible
if (runtimeService.canPerformAutomaticStepBack("currentTask", "processInstanceId")) {
    // Perform step back
    runtimeService.performAutomaticStepBack("currentTask", "processInstanceId");
}

// Get all possible step back targets
List<String> possibleTargets = runtimeService.getPossibleStepBackTargets("currentTask", "processInstanceId");
```

## Supported Workflow Scenarios

### 1. Simple Sequential Flow
- Standard task-to-task navigation
- Automatic identification of previous user task

### 2. Parallel Gateway Scenarios  
- Handles complex parallel execution paths
- Identifies tasks from multiple branches
- Proper cleanup of parallel executions

### 3. Subprocess Scenarios
- Navigation within subprocess boundaries
- Cross-subprocess step back support
- Proper scope handling

## Error Handling

The implementation provides comprehensive error handling:

- **Validation**: Ensures task and process instance exist
- **Workflow Analysis**: Validates step back targets are reachable
- **Execution Safety**: Prevents invalid state transitions
- **Logging**: Detailed logging for debugging and monitoring

## Technical Details

### Command Pattern Integration
- Follows Flowable's command pattern for consistency
- Commands: `PerformAutomaticStepBackCmd`, `CanPerformAutomaticStepBackCmd`, `GetPossibleStepBackTargetsCmd`
- Proper transaction handling and rollback support

### Performance Considerations
- Efficient BPMN model analysis
- Caching of workflow structure analysis
- Minimal impact on existing process execution

### Backwards Compatibility
- No breaking changes to existing APIs
- Optional functionality - existing workflows unaffected
- Consistent with Flowable design patterns

## Testing

Comprehensive test coverage includes:
- Unit tests for individual components
- Integration tests for API functionality
- Error handling and edge case validation
- Performance and load testing scenarios

## Limitations

Current implementation limitations:
- Limited to user tasks (other activity types not supported)
- Requires active process instances
- May need customization for complex business rules

## Future Enhancements

Potential future improvements:
- Support for other activity types
- Advanced target selection algorithms
- Integration with process mining for intelligent step back
- Business rule-based target selection
- UI components for step back operations

## Conclusion

The automatic step back functionality simplifies workflow operations by automatically determining appropriate previous tasks, supporting complex workflow patterns while maintaining the robustness and consistency expected from Flowable Engine.