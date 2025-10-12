# patra-ingest-api — Ingest API Contracts

> **API module** defining the public contract for the ingest service — error codes and future task worker APIs.

---

## 📌 Purpose

`patra-ingest-api` provides the **external API contract** for interacting with the ingest service from other microservices. Currently in **bootstrap phase**, it contains:

1. **Error Codes**: Standardized error handling for ingest operations
2. **Future APIs** (Planned): Task worker APIs for external task execution

**Why separate API module?**
- **Decoupling**: Consumers depend only on contracts, not implementation
- **Versioning**: API contracts evolve independently
- **Type Safety**: Compile-time verification of RPC calls

---

## 🗂️ Module Structure

```
patra-ingest-api/
└─ src/main/java/.../api/
   └─ error/                         # Error Codes
      └─ IngestErrorCode.java           # Standardized error codes
```

**Current Status**: Bootstrap phase — only error codes defined.

**Planned Additions**:
- `dto/` - Request/Response DTOs for task worker APIs
- `endpoint/` - Task worker endpoint interfaces
- `client/` - Feign clients for task workers

---

## ⚠️ Error Codes

### IngestErrorCode

**Standardized error codes** following pattern: `ING-{segment}{number}`

```java
public final class IngestErrorCode {

    // HTTP-aligned errors (0xxx segment)
    public static final ErrorCodeLike BAD_REQUEST = ...;           // ING-0400
    public static final ErrorCodeLike NOT_FOUND = ...;             // ING-0404
    public static final ErrorCodeLike UNPROCESSABLE = ...;         // ING-0422
    public static final ErrorCodeLike INTERNAL_ERROR = ...;        // ING-0500

    // Business errors (1xxx+ segment)
    public static final ErrorCodeLike PLAN_NOT_FOUND = ...;        // ING-1001
    public static final ErrorCodeLike PLAN_ALREADY_EXISTS = ...;   // ING-1002
    public static final ErrorCodeLike TASK_NOT_FOUND = ...;        // ING-1003
    public static final ErrorCodeLike TASK_ALREADY_COMPLETED = ...; // ING-1004
    public static final ErrorCodeLike WINDOW_INVALID = ...;        // ING-1005
    public static final ErrorCodeLike CAPACITY_EXCEEDED = ...;     // ING-1006
    public static final ErrorCodeLike IDEMPOTENCY_CONFLICT = ...;  // ING-1007
}
```

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `ING-0400` | 400 | Bad Request |
| `ING-0404` | 404 | Plan/Task Not Found |
| `ING-0422` | 422 | Unprocessable Entity (validation failed) |
| `ING-0500` | 500 | Internal Server Error |
| `ING-1001` | 404 | Plan not found |
| `ING-1002` | 409 | Plan already exists (idempotency conflict) |
| `ING-1003` | 404 | Task not found |
| `ING-1004` | 409 | Task already completed |
| `ING-1005` | 422 | Window invalid (empty, too large, etc.) |
| `ING-1006` | 429 | Capacity exceeded (too many queued tasks) |
| `ING-1007` | 409 | Idempotency conflict (duplicate idempotent key) |

---

## 🔮 Future APIs (Planned)

### Task Worker APIs

**Purpose**: Allow external task workers to:
1. Poll for queued tasks
2. Lease tasks for execution
3. Report task status (running, succeeded, failed)
4. Update task progress

**Planned Endpoints**:

```java
// Future: TaskWorkerEndpoint.java
public interface TaskWorkerEndpoint {
    String BASE_PATH = "/_internal/tasks";

    // Poll for queued tasks
    @GetMapping(BASE_PATH + "/poll")
    List<TaskDTO> pollTasks(
        @RequestParam("provenanceCode") String provenanceCode,
        @RequestParam("limit") int limit
    );

    // Lease task for execution
    @PostMapping(BASE_PATH + "/{taskId}/lease")
    TaskLeaseResp leaseTask(
        @PathVariable("taskId") Long taskId,
        @RequestBody TaskLeaseReq request
    );

    // Report task status
    @PutMapping(BASE_PATH + "/{taskId}/status")
    void updateTaskStatus(
        @PathVariable("taskId") Long taskId,
        @RequestBody TaskStatusUpdateReq request
    );

    // Heartbeat to keep lease alive
    @PostMapping(BASE_PATH + "/{taskId}/heartbeat")
    void heartbeat(@PathVariable("taskId") Long taskId);
}
```

**Planned DTOs**:

```java
// Future: TaskDTO.java
public record TaskDTO(
    Long id,
    String idempotentKey,
    String provenanceCode,
    String operationType,
    String paramsJson,
    Integer priority,
    Instant scheduledAt
) {}

// Future: TaskLeaseResp.java
public record TaskLeaseResp(
    Long taskId,
    String leaseId,
    Instant leasedUntil
) {}

// Future: TaskStatusUpdateReq.java
public record TaskStatusUpdateReq(
    String status,           // RUNNING, SUCCEEDED, FAILED
    String resultJson,       // Result data
    String errorMessage      // Error message if failed
) {}
```

**Planned Feign Client**:

```java
// Future: TaskWorkerClient.java
@FeignClient(name = "patra-ingest", contextId = "taskWorkerClient")
public interface TaskWorkerClient extends TaskWorkerEndpoint {
}
```

---

## 🚀 Usage Guide (Future)

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-ingest-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 2: Enable Feign Clients

```java
@SpringBootApplication
@EnableFeignClients(clients = TaskWorkerClient.class)
public class TaskWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskWorkerApplication.class, args);
    }
}
```

### Step 3: Poll and Execute Tasks

```java
@Component
@RequiredArgsConstructor
public class PubMedTaskWorker {

    private final TaskWorkerClient taskWorkerClient;
    private final EgressGatewayClient egressClient;

    @Scheduled(fixedDelay = 5000)
    public void pollAndExecute() {
        // Poll for queued tasks
        List<TaskDTO> tasks = taskWorkerClient.pollTasks("pubmed", 10);

        for (TaskDTO task : tasks) {
            try {
                // Lease task
                TaskLeaseResp lease = taskWorkerClient.leaseTask(
                    task.id(),
                    new TaskLeaseReq("worker-01", 300)  // 5 min lease
                );

                // Execute task
                executeTask(task);

                // Report success
                taskWorkerClient.updateTaskStatus(
                    task.id(),
                    new TaskStatusUpdateReq("SUCCEEDED", resultJson, null)
                );
            } catch (Exception ex) {
                // Report failure
                taskWorkerClient.updateTaskStatus(
                    task.id(),
                    new TaskStatusUpdateReq("FAILED", null, ex.getMessage())
                );
            }
        }
    }

    private void executeTask(TaskDTO task) {
        // Parse task params
        TaskParams params = parseTaskParams(task.paramsJson());

        // Call external API via egress gateway
        ExternalCallResponseDTO response = egressClient.call(
            new ExternalCallRequestDTO(
                params.url(),
                "GET",
                params.headers(),
                null,
                params.resilienceConfig()
            )
        );

        // Process response
        // ...
    }
}
```

---

## 📊 Current Status

### ✅ Completed

- ✅ Error codes defined (IngestErrorCode)
- ✅ API module structure

### 🚧 In Progress

- 🚧 Task worker API design
- 🚧 Lease management pattern
- 🚧 Heartbeat mechanism

### 📋 Planned

- 📋 Task worker endpoint interfaces
- 📋 Task DTOs (TaskDTO, TaskLeaseResp, etc.)
- 📋 Feign client (TaskWorkerClient)
- 📋 Task status update APIs
- 📋 Task query APIs (for monitoring)

---

## 🔗 Related Documentation

- [patra-ingest Service README](../README.md)
- [Main README](../../README.md)
- [Architecture Guide](../../docs/ARCHITECTURE.md)
- [Plan Ingestion Flow](../patra-ingest-app/usecase/plan/README.md)

---

**Last Updated**: 2025-01-12
**Status**: Bootstrap Phase — Error Codes Only
