# patra-ingest-api — 摄入服务 API 契约

> **API 模块**,定义摄入服务的外部契约 — 错误码和未来的 Task Worker APIs。

---

## 概述

`patra-ingest-api` 提供 **patra-ingest 服务的外部 API 契约**,用于与其他微服务交互。当前处于**初始阶段**,包含:

1. **错误码(Error Codes)**: 标准化的错误处理
2. **未来 APIs(Planned)**: Task Worker APIs(用于外部任务执行)

**为什么独立 API 模块?**
- **解耦(Decoupling)**: 消费者仅依赖契约,不依赖实现
- **版本控制(Versioning)**: API 契约独立演进
- **类型安全(Type Safety)**: 编译时验证 RPC 调用

---

## 模块结构

```
patra-ingest-api/
└─ src/main/java/.../api/
   └─ error/                         # 错误码
      └─ IngestErrorCode.java            # 标准化错误码
```

**当前状态**: 初始阶段 — 仅定义错误码

**计划新增**:
- `dto/` - Task Worker APIs 的请求/响应 DTOs
- `endpoint/` - Task Worker 端点接口
- `client/` - Feign 客户端(供 Task Workers 使用)

---

## 错误码

### IngestErrorCode

**标准化错误码**,遵循模式: `ING-{segment}{number}`

```java
public final class IngestErrorCode {

    // HTTP 对齐错误(0xxx 段)
    public static final ErrorCodeLike BAD_REQUEST = ...;           // ING-0400
    public static final ErrorCodeLike NOT_FOUND = ...;             // ING-0404
    public static final ErrorCodeLike UNPROCESSABLE = ...;         // ING-0422
    public static final ErrorCodeLike INTERNAL_ERROR = ...;        // ING-0500

    // 业务错误(1xxx+ 段)
    public static final ErrorCodeLike PLAN_NOT_FOUND = ...;        // ING-1001
    public static final ErrorCodeLike PLAN_ALREADY_EXISTS = ...;   // ING-1002
    public static final ErrorCodeLike TASK_NOT_FOUND = ...;        // ING-1003
    public static final ErrorCodeLike WINDOW_INVALID = ...;        // ING-1005
    public static final ErrorCodeLike CAPACITY_EXCEEDED = ...;     // ING-1006
}
```

| 错误码 | HTTP 状态 | 说明 |
|--------|-----------|------|
| `ING-0400` | 400 | 错误请求 |
| `ING-0404` | 404 | Plan/Task 未找到 |
| `ING-0422` | 422 | 无法处理的实体(验证失败) |
| `ING-0500` | 500 | 内部服务错误 |
| `ING-1001` | 404 | Plan 未找到 |
| `ING-1002` | 409 | Plan 已存在(幂等性冲突) |
| `ING-1003` | 404 | Task 未找到 |
| `ING-1005` | 422 | 窗口无效(空、过大等) |
| `ING-1006` | 429 | 容量超限(排队任务过多) |

---

## 未来 APIs (规划中)

### Task Worker APIs

**目的**: 允许外部 Task Workers:
1. 轮询排队任务
2. 租用任务用于执行
3. 报告任务状态(运行中、成功、失败)
4. 更新任务进度

**规划端点**:

```java
// 未来: TaskWorkerEndpoint.java
public interface TaskWorkerEndpoint {
    String BASE_PATH = "/_internal/tasks";

    // 轮询排队任务
    @GetMapping(BASE_PATH + "/poll")
    List<TaskDTO> pollTasks(
        @RequestParam("provenanceCode") String provenanceCode,
        @RequestParam("limit") int limit
    );

    // 租用任务
    @PostMapping(BASE_PATH + "/{taskId}/lease")
    TaskLeaseResp leaseTask(
        @PathVariable("taskId") Long taskId,
        @RequestBody TaskLeaseReq request
    );

    // 更新任务状态
    @PutMapping(BASE_PATH + "/{taskId}/status")
    void updateTaskStatus(
        @PathVariable("taskId") Long taskId,
        @RequestBody TaskStatusUpdateReq request
    );

    // 心跳续约
    @PostMapping(BASE_PATH + "/{taskId}/heartbeat")
    void heartbeat(@PathVariable("taskId") Long taskId);
}
```

**规划 DTOs**:

```java
// 未来: TaskDTO.java
public record TaskDTO(
    Long id,
    String idempotentKey,
    String provenanceCode,
    String operationType,
    String paramsJson,
    Integer priority,
    Instant scheduledAt
) {}

// 未来: TaskLeaseResp.java
public record TaskLeaseResp(
    Long taskId,
    String leaseId,
    Instant leasedUntil
) {}

// 未来: TaskStatusUpdateReq.java
public record TaskStatusUpdateReq(
    String status,           // RUNNING, SUCCEEDED, FAILED
    String resultJson,       // 结果数据
    String errorMessage      // 失败错误信息
) {}
```

---

## 依赖关系

### 上游依赖
- `patra-common-core`: 通用工具和枚举
- `jakarta.validation-api`: 验证 API

### 下游消费者
- `patra-ingest-app`: 应用层(使用错误码)
- `patra-ingest-adapter`: 适配器层(使用错误码)
- **未来**: 外部 Task Worker 服务(使用 Feign 客户端)

---

## 技术栈

- **Java**: 25
- **Jakarta Validation API**: 3.x
- **Lombok**: 编译时注解

---

**最后更新**: 2025-01-16
**Maven 坐标**: `com.papertrace:patra-ingest-api:0.1.0-SNAPSHOT`
**状态**: 初始阶段 — 仅错误码
