# patra-catalog-adapter — 目录管理适配器层

## 📋 概述

`patra-catalog-adapter` 是 patra-catalog 服务的**适配器层（Adapter Layer）**，负责处理外部交互，包括 REST API、消息监听、定时任务等，是系统的**入口（Driving Adapter）**。

本模块在六边形架构中位于**外层**，遵循以下原则：
- **单向依赖**：Adapter 层依赖 Application 层，不直接调用 Domain 层或 Infrastructure 层
- **薄 Controller**：Controller 只负责参数验证和调用 Orchestrator，不包含业务逻辑
- **异常委托**：Controller 不处理异常，委托给全局异常处理器
- **Assembler 模式**：使用 Assembler 将 HTTP 请求转换为命令对象
- **统一响应格式**：所有 API 使用统一的响应格式

---

## 🏗️ 模块结构

```
patra-catalog-adapter/
└─ src/main/java/.../adapter/
   ├─ rest/                        # REST API 控制器
   │  ├─ MeshImportController.java            # MeSH 导入管理 API
   │  ├─ request/                             # HTTP 请求对象
   │  │  └─ StartImportRequest.java           # 开始导入请求
   │  └─ assembler/                           # Assembler 模式对象转换器
   │     └─ StartImportAssembler.java         # 开始导入请求转换器
   ├─ scheduler/                   # 定时任务
   │  └─ job/                                 # XXL-Job 任务执行器
   │     └─ MeshImportJob.java                # MeSH 数据导入任务执行器
   └─ rocketmq/                    # RocketMQ 消息监听器（规划中）
      └─ TaskReadyMessageListener.java        # 任务就绪消息监听器
```

---

## 🔑 核心职责

### 1. REST API

**职责**：提供 HTTP 接口供客户端调用。

**端点列表**：
| 方法 | 路径 | 说明 | 状态 |
|-----|------|------|------|
| POST | `/api/v1/mesh/import/start` | 开始导入任务 | ✅ 已实现 |
| POST | `/api/v1/mesh/import/retry/{taskId}` | 重试失败任务 | ✅ 已实现 |
| POST | `/api/v1/mesh/import/clear` | 清除数据重新导入 | ✅ 已实现 |
| GET | `/api/v1/mesh/import/progress/{taskId}` | 查询导入进度 | 🚧 规划中 |
| GET | `/api/v1/mesh/import/tasks` | 查询所有导入任务 | 🚧 规划中 |

### 2. 定时任务

**职责**：通过 XXL-Job 定期执行导入任务。

**任务列表**：
| 任务名称 | Cron 表达式 | 说明 | 状态 |
|---------|-----------|------|------|
| `meshImport` | `0 0 2 * * ?` | 每天凌晨 2 点执行 MeSH 导入 | ✅ 已实现 |

**分布式锁**：使用 Redisson 分布式锁避免并发导入。

### 3. 消息监听

**职责**：订阅 RocketMQ 消息触发业务流程（规划中）。

### 4. 请求验证

**职责**：验证 HTTP 请求参数的合法性。

**验证策略**：
- 使用 JSR-303 Bean Validation（`@Valid`、`@NotNull`、`@NotBlank`）
- 自定义验证注解（如 `@ValidUrl`）

### 5. 异常转换

**职责**：将领域异常转换为 HTTP 响应（委托给全局异常处理器）。

**映射规则**（由 `MeshImportErrorMappingContributor` 定义）：
| 异常类型 | HTTP 状态码 | 说明 |
|---------|-----------|------|
| `IllegalStateException` | 409 Conflict | 业务状态冲突 |
| `IllegalArgumentException`（"任务不存在"） | 404 Not Found | 资源不存在 |
| `IllegalArgumentException`（其他） | 400 Bad Request | 参数错误 |
| `RuntimeException` | 500 Internal Server Error | 服务器错误 |

---

## 🎯 核心组件

### 1. MeshImportController (MeSH 导入管理 API)

**职责**：提供 MeSH 数据导入相关的 REST API。

**核心方法**：

#### POST /api/v1/mesh/import/start

开始 MeSH 数据导入任务。

**请求**：
```json
{
  "sourceUrl": "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
  "taskName": "2025年MeSH数据导入"
}
```

**响应**：
```json
{
  "taskId": "123456789",
  "taskName": "2025年MeSH数据导入",
  "status": "PROCESSING",
  "startedAt": "2025-11-22T10:00:00Z"
}
```

**实现**：
```java
@PostMapping("/start")
public ResponseEntity<MeshImportResultDTO> startImport(
    @RequestBody @Valid StartImportRequest request
) {
    // 1. 转换请求对象为命令对象
    StartImportCommand command = startImportAssembler.toCommand(request);

    // 2. 调用 Application 层编排器
    MeshImportResultDTO result = meshImportOrchestrator.startImport(command);

    // 3. 返回响应
    return ResponseEntity.ok(result);
}
```

**文件**：`rest/MeshImportController.java`

#### POST /api/v1/mesh/import/retry/{taskId}

重试失败的导入任务。

**请求**：
```http
POST /api/v1/mesh/import/retry/123456789
```

**响应**：
```json
{
  "taskId": "123456789",
  "taskName": "2025年MeSH数据导入",
  "status": "PROCESSING",
  "startedAt": "2025-11-22T10:30:00Z"
}
```

**实现**：
```java
@PostMapping("/retry/{taskId}")
public ResponseEntity<MeshImportResultDTO> retryFailedTask(
    @PathVariable String taskId
) {
    MeshImportId importId = MeshImportId.of(Long.parseLong(taskId));
    MeshImportResultDTO result = meshImportOrchestrator.retryFailedTask(importId);
    return ResponseEntity.ok(result);
}
```

**文件**：`rest/MeshImportController.java`

#### POST /api/v1/mesh/import/clear

清除所有 MeSH 数据，重新开始导入。

**警告**：⚠️ 此操作会删除所有 MeSH 数据，仅用于首次导入或数据完全损坏时。

**请求**：
```http
POST /api/v1/mesh/import/clear
```

**响应**：
```http
HTTP/1.1 200 OK
```

**实现**：
```java
@PostMapping("/clear")
public ResponseEntity<Void> clearAndRestart() {
    meshImportOrchestrator.clearAndRestart();
    return ResponseEntity.ok().build();
}
```

**文件**：`rest/MeshImportController.java`

### 2. MeshImportJob (MeSH 数据导入任务执行器)

**职责**：通过 XXL-Job 定期执行 MeSH 导入任务。

**核心特性**：

#### 分布式锁

使用 Redisson 分布式锁避免并发导入：
```java
RLock lock = redissonClient.getLock("mesh:import:lock");

if (!lock.tryLock()) {
    log.warn("MeSH 导入任务正在运行中，跳过本次执行");
    return;
}

try {
    // 执行导入
} finally {
    lock.unlock();
}
```

#### 任务执行

```java
@XxlJob("meshImport")
public void run() {
    RLock lock = redissonClient.getLock("mesh:import:lock");

    if (!lock.tryLock()) {
        log.warn("MeSH 导入任务正在运行中，跳过本次执行");
        return;
    }

    try {
        log.info("开始执行 MeSH 导入任务");

        // 调用 Application 层编排器
        StartImportCommand command = new StartImportCommand(null, null);
        MeshImportResultDTO result = meshImportOrchestrator.startImport(command);

        log.info("MeSH 导入任务执行完成：{}", result);

    } catch (Exception ex) {
        log.error("MeSH 导入任务执行失败", ex);
        throw ex;
    } finally {
        lock.unlock();
    }
}
```

**XXL-Job 配置**（在 XXL-Job 控制台配置）：
- **任务名称**：meshImport
- **Cron 表达式**：`0 0 2 * * ?`（每天凌晨 2 点）
- **任务参数**：无（使用默认配置）

**文件**：`scheduler/job/MeshImportJob.java`

### 3. StartImportAssembler (开始导入请求转换器)

**职责**：将 HTTP 请求对象转换为命令对象。

**实现**：
```java
@Component
public class StartImportAssembler {

    public StartImportCommand toCommand(StartImportRequest request) {
        return new StartImportCommand(
            request.getSourceUrl(),
            request.getExpectedMd5()
        );
    }
}
```

**设计理由**：
- **解耦**：Controller 不直接依赖 Request 对象
- **可测试**：Assembler 可以独立测试
- **符合 DDD**：将 HTTP 层概念（Request）与应用层概念（Command）分离

**文件**：`rest/assembler/StartImportAssembler.java`

---

## 📦 依赖关系

### 上游依赖

- `patra-catalog-app`：应用层（Orchestrator）
- `patra-catalog-domain`：领域模型（仅用于类型安全，如 `MeshImportId`）
- `Spring Boot Web`：@RestController、@RequestMapping 等
- `Spring Validation`：@Valid、@NotNull 等
- `XXL-Job`：@XxlJob
- `Redisson`：分布式锁

### 下游消费者

- **HTTP 客户端**：前端应用、其他服务
- **XXL-Job 调度器**：触发定时任务

**依赖方向**：Domain ← App ← Adapter（符合六边形架构）

---

## 💡 使用示例

### 示例 1：REST API 控制器

```java
@RestController
@RequestMapping("/api/v1/mesh/import")
@RequiredArgsConstructor
public class MeshImportController {

    private final MeshImportOrchestrator meshImportOrchestrator;
    private final StartImportAssembler startImportAssembler;

    @PostMapping("/start")
    public ResponseEntity<MeshImportResultDTO> startImport(
        @RequestBody @Valid StartImportRequest request
    ) {
        // 1. 转换请求对象为命令对象
        StartImportCommand command = startImportAssembler.toCommand(request);

        // 2. 调用 Application 层编排器
        MeshImportResultDTO result = meshImportOrchestrator.startImport(command);

        // 3. 返回响应
        return ResponseEntity.ok(result);
    }

    @PostMapping("/retry/{taskId}")
    public ResponseEntity<MeshImportResultDTO> retryFailedTask(
        @PathVariable String taskId
    ) {
        MeshImportId importId = MeshImportId.of(Long.parseLong(taskId));
        MeshImportResultDTO result = meshImportOrchestrator.retryFailedTask(importId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/clear")
    public ResponseEntity<Void> clearAndRestart() {
        meshImportOrchestrator.clearAndRestart();
        return ResponseEntity.ok().build();
    }
}
```

### 示例 2：XXL-Job 定时任务

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MeshImportJob {

    private final MeshImportOrchestrator meshImportOrchestrator;
    private final RedissonClient redissonClient;

    @XxlJob("meshImport")
    public void run() {
        RLock lock = redissonClient.getLock("mesh:import:lock");

        // 尝试获取分布式锁（避免并发导入）
        if (!lock.tryLock()) {
            log.warn("MeSH 导入任务正在运行中，跳过本次执行");
            return;
        }

        try {
            log.info("开始执行 MeSH 导入任务");

            // 调用 Application 层编排器
            StartImportCommand command = new StartImportCommand(null, null);
            MeshImportResultDTO result = meshImportOrchestrator.startImport(command);

            log.info("MeSH 导入任务执行完成：{}", result);

        } catch (Exception ex) {
            log.error("MeSH 导入任务执行失败", ex);
            throw ex;
        } finally {
            lock.unlock();
        }
    }
}
```

### 示例 3：Assembler 模式对象转换

```java
@Component
public class StartImportAssembler {

    public StartImportCommand toCommand(StartImportRequest request) {
        return new StartImportCommand(
            request.getSourceUrl(),
            request.getExpectedMd5()
        );
    }
}
```

### 示例 4：请求参数验证

```java
@Data
public class StartImportRequest {

    @NotBlank(message = "sourceUrl 不能为空")
    @ValidUrl(message = "sourceUrl 格式不正确")
    private String sourceUrl;

    @Size(max = 100, message = "taskName 长度不能超过 100")
    private String taskName;

    private String expectedMd5;
}
```

---

## 🎨 设计模式

### 1. Assembler 模式

**核心思想**：将 HTTP 层对象（Request）与应用层对象（Command）分离。

**优点**：
- 解耦 HTTP 层和应用层
- 可以独立测试
- 支持多种表示层（REST、GraphQL、gRPC）

### 2. 薄 Controller 模式

**核心思想**：Controller 只负责参数验证和调用 Orchestrator，不包含业务逻辑。

**特点**：
- 业务逻辑封装在 Domain 层和 Application 层
- Controller 只做**协议适配**（HTTP → Application）
- 易于测试（Controller 测试只验证参数验证和调用是否正确）

### 3. 异常委托模式

**核心思想**：Controller 不处理异常，委托给全局异常处理器。

**优点**：
- 集中管理异常映射规则
- 避免 Controller 代码重复
- 符合关注点分离原则

---

## 🧪 测试覆盖

| 测试类型 | 覆盖率目标 | 当前覆盖率 |
|---------|-----------|-----------|
| 单元测试 | ≥70% | [待测试运行后更新] |
| 切片测试 | 100%（REST API） | [待测试运行后更新] |

**关键测试类**：
- `MeshImportControllerTest` - REST API 切片测试（`@WebMvcTest`）
- `MeshImportJobTest` - 定时任务单元测试
- `StartImportAssemblerTest` - Assembler 单元测试

---

## 🛠️ 技术栈

- **Spring Boot Web**：3.5.7
- **Spring Validation**：Bean Validation（JSR-303）
- **XXL-Job**：分布式任务调度
- **Redisson**：分布式锁
- **Lombok**：编译时注解

---

## 📚 相关文档

- [patra-catalog 模块总览](../README.md)
- [patra-catalog-app 应用层文档](../patra-catalog-app/README.md)
- [MeSH 导入 API 规范](../../specs/001-mesh-data-import/contracts/mesh-import-api.yaml)
- [XXL-Job 官方文档](https://www.xuxueli.com/xxl-job/)
- [Redisson 官方文档](https://redisson.org/)

---

**最后更新**：2025-11-22
**Maven 坐标**：`com.patra:patra-catalog-adapter:0.2.0-SNAPSHOT`
**作者**：Patra Team
