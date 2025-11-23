/// 异常映射包。
///
/// 实现业务异常到 HTTP 状态码的映射，集成 Patra Starter Web 的错误处理机制。
///
/// ## 职责
///
/// - **异常映射**：将标准异常类型映射到特定的 HTTP 状态码和业务错误码
///   - **错误码生成**：生成符合规范的业务错误码（如 CATALOG-0409）
///   - **优先级控制**：覆盖框架默认的异常映射规则
///   - **集中管理**：统一管理 Catalog 微服务的异常映射规则
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.app.error.MeshImportErrorMappingContributor} - MeSH 导入异常映射贡献者
///
/// - 映射 {@link IllegalStateException} → 409 Conflict（业务状态冲突）
///       - 映射 {@link IllegalArgumentException}（任务不存在） → 404 Not Found
///       - 映射 {@link IllegalArgumentException}（参数错误） → 400 Bad Request
///
/// ## 设计理念
///
/// **简单性原则**：不创建自定义异常类，复用 JDK 标准异常（IllegalStateException、IllegalArgumentException）：
///
/// - ✅ **优势**：减少代码量，避免异常类爆炸
/// - ✅ **实用性**：通过异常消息区分业务语义
/// - ✅ **集中管理**：异常到 HTTP 状态码的映射集中在 Contributor
///
/// **DRY 原则**：错误消息只在抛出异常时定义一次，无需在 ErrorCode 中重复：
///
/// ```java
/// // ❌ 旧方式：定义专用异常类 + ErrorCode
/// public class TaskNotFoundException extends RuntimeException { ... }
/// public enum CatalogErrorCode { TASK_NOT_FOUND(404, "任务不存在") }
///
/// // ✅ 新方式：使用标准异常 + 消息 + Contributor 映射
/// throw new IllegalArgumentException("任务不存在：" + taskId);
/// // Contributor 自动映射为 404 Not Found
/// ```
///
/// ## 异常映射规则
///
/// | 异常类型 | 消息条件 | HTTP 状态码 | 错误码 | 使用场景 |
/// |---------|---------|------------|--------|---------|
/// | IllegalStateException | - | 409 Conflict | CATALOG-0409 | 业务状态冲突（如已有任务运行） |
/// | IllegalArgumentException | 包含"任务不存在" | 404 Not Found | CATALOG-0404 | 资源未找到 |
/// | IllegalArgumentException | 其他 | 400 Bad Request | CATALOG-0400 | 参数错误 |
/// | ConstraintViolationException | - | 400 Bad Request | (默认) | Jakarta Validation 校验失败 |
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：业务状态冲突（409 Conflict）
/// if (existingTask != null && existingTask.isRunning()) {
///     throw new IllegalStateException("已有任务正在运行，无法启动新任务");
/// }
/// // → 自动映射为 HTTP 409，错误码 CATALOG-0409
///
/// // 示例 2：资源未找到（404 Not Found）
/// MeshImportAggregate task = repository.findById(taskId)
///     .orElseThrow(() -> new IllegalArgumentException("任务不存在：" + taskId));
/// // → 消息包含"任务不存在"，自动映射为 HTTP 404，错误码 CATALOG-0404
///
/// // 示例 3：参数错误（400 Bad Request）
/// if (batchSize <= 0) {
///     throw new IllegalArgumentException("批次大小必须大于0：" + batchSize);
/// }
/// // → 自动映射为 HTTP 400，错误码 CATALOG-0400
///
/// // 示例 4：最终生成的 RFC 7807 响应
/// // HTTP/1.1 409 Conflict
/// // Content-Type: application/problem+json
/// //
/// // {
/// //   "type": "about:blank",
/// //   "title": "Conflict",
/// //   "status": 409,
/// //   "detail": "已有任务正在运行，无法启动新任务",
/// //   "instance": "/api/v1/mesh/import/start",
/// //   "errorCode": "CATALOG-0409"
/// // }
/// ```
///
/// ## 优先级机制
///
/// Patra Starter Web 的异常处理器按以下优先级查找映射规则：
///
/// 1. **ErrorMappingContributor**（最高优先级） - 业务自定义映射
/// 2. **类名启发式规则** - 框架默认规则（如 IllegalXxxException → 422）
/// 3. **默认映射** - 500 Internal Server Error
///
/// MeshImportErrorMappingContributor 返回 `Optional.of(ErrorCode)` 时，覆盖框架默认规则。
///
/// ## 架构位置
///
/// **App 层 - 错误处理**：
///
/// - 桥接 Domain 层异常和 Adapter 层 HTTP 响应
/// - 与 GlobalRestExceptionHandler 配合工作
/// - 不包含业务逻辑，仅提供映射规则
///
/// @author linqibin
/// @since 0.1.0
/// @see com.patra.starter.core.error.spi.ErrorMappingContributor
/// @see com.patra.starter.web.error.handler.GlobalRestExceptionHandler
package com.patra.catalog.app.error;
