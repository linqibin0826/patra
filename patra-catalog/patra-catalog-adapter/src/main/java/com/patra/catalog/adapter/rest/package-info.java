/// REST API 控制器包。
///
/// 提供 Catalog 微服务的 HTTP RESTful API 接口，遵循 RFC 7807 Problem Details 标准。
///
/// ## 职责
///
/// - **接收 HTTP 请求**：处理来自客户端的 HTTP 请求，验证输入参数
///   - **调用编排器**：委派业务逻辑到 App 层的 Orchestrator
///   - **异常处理**：将领域异常映射为符合 RFC 7807 的错误响应
///   - **格式转换**：将领域对象转换为 API 响应对象（DTO）
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.adapter.rest.MeshImportController} - MeSH 数据导入 API 控制器
///
/// - 提供 POST /api/v1/mesh/import/start - 启动导入任务
///       - 提供 POST /api/v1/mesh/import/retry/{taskId} - 重试失败任务
///       - 提供 POST /api/v1/mesh/import/clear - 清除进度重新开始
///       - 提供 GET /api/v1/mesh/import/progress/{taskId} - 查询导入进度
///
/// ## 设计原则
///
/// - **薄适配器**：控制器只负责协议转换，不包含业务逻辑
///   - **参数校验**：使用 Jakarta Validation（@Valid、@NotNull）进行输入校验
///   - **异常映射**：依赖全局异常处理器自动转换异常为 RFC 7807 格式
///   - **幂等性**：重要操作（如重试）支持幂等调用
///
/// ## 异常处理
///
/// 异常由 {@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler} 统一处理，映射规则由 {@link
/// com.patra.catalog.app.error.MeshImportErrorMappingContributor} 定义：
///
/// - {@link IllegalStateException} → 409 Conflict（业务状态冲突）
///   - {@link IllegalArgumentException}（任务不存在） → 404 Not Found
///   - {@link IllegalArgumentException}（参数错误） → 400 Bad Request
///   - {@link jakarta.validation.ConstraintViolationException} → 400 Bad Request（校验失败）
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：启动 MeSH 导入任务（使用配置文件默认值）
/// POST /api/v1/mesh/import/start
/// HTTP/1.1 200 OK
/// Content-Type: application/json
///
/// {
///   "taskId": "1234567890",
///   "taskName": "2025年MeSH数据首次导入",
///   "status": "PROCESSING",
///   "startTime": "2025-01-15T10:00:00Z",
///   "message": "任务已启动，正在下载 XML 文件..."
/// }
///
/// // 示例 2：重试失败任务
/// POST /api/v1/mesh/import/retry/1234567890
/// HTTP/1.1 200 OK
///
/// // 示例 3：查询导入进度
/// GET /api/v1/mesh/import/progress/1234567890
/// HTTP/1.1 200 OK
/// Content-Type: application/json
///
/// {
///   "taskId": "1234567890",
///   "status": "PROCESSING",
///   "tableProgressList": [
///     {
///       "tableName": "descriptor",
///       "totalCount": 30000,
///       "processedCount": 15000,
///       "status": "PROCESSING"
///     }
///   ]
/// }
///
/// // 示例 4：业务状态冲突错误
/// POST /api/v1/mesh/import/start
/// HTTP/1.1 409 Conflict
/// Content-Type: application/problem+json
///
/// {
///   "type": "about:blank",
///   "title": "Conflict",
///   "status": 409,
///   "detail": "已有任务正在运行，无法启动新任务",
///   "instance": "/api/v1/mesh/import/start"
/// }
/// ```
///
/// ## 架构位置
///
/// **Adapter 层 - REST 适配器**：
///
/// - 六边形架构的入站适配器（Inbound Adapter）
/// - 将 HTTP 协议转换为领域操作调用
/// - 不直接调用 Domain 层，通过 App 层 Orchestrator 编排
///
/// ## API 设计规范
///
/// - **URL 规范**：RESTful 风格，使用名词复数（/mesh/import）
///   - **HTTP 方法**：POST（创建/操作）、GET（查询）、PUT（更新）、DELETE（删除）
///   - **版本控制**：URL 路径版本（/api/v1）
///   - **响应格式**：统一使用 JSON，遵循 RFC 7807 错误格式
///   - **状态码使用**：200（成功）、400（请求错误）、404（未找到）、409（冲突）、500（服务器错误）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.adapter.rest;
