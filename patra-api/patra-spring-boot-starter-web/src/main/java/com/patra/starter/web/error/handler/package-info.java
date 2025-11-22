/// Web 全局异常处理器包。
///
/// 本包提供基于 Spring `@RestControllerAdvice` 的全局异常处理器, 自动捕获所有未处理的异常并转换为符合 RFC 7807 标准的
/// ProblemDetail 响应。
///
/// ## 职责
///
/// - 捕获所有未处理的异常(`@ExceptionHandler(Exception.class)`)
///   - 将异常转换为 RFC 7807 ProblemDetail 响应
///   - 特殊处理参数验证异常(`MethodArgumentNotValidException`)
///   - 记录详细的错误日志(包含追踪 ID、请求路径等上下文)
///
/// ## 核心组件
///
/// - {@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler} - 全局 REST 异常处理器
///
/// ## 处理流程
///
/// ```
///
/// 异常抛出
///   ↓
/// GlobalRestExceptionHandler.handleException(Exception ex)
///   ↓
/// ProblemDetailAdapter.adapt(ex, request)
///   ├─ ErrorResolutionPipeline.resolve(ex)        - 解析错误码和 HTTP 状态
///   └─ ProblemDetailBuilder.build(...)            - 构建 ProblemDetail
///   ↓
/// ResponseEntity<ProblemDetail>
///   ├─ HTTP Status: 根据错误类型(400/404/500 等)
///   ├─ Content-Type: application/problem+json
///   └─ Body: RFC 7807 ProblemDetail
///
/// ```
///
/// ## 异常处理示例
///
/// ### 通用异常处理
///
/// ```java
/// // 业务代码抛出异常
/// @GetMapping("/api/plans/{id")
/// public ApiResponse<PlanResponse> getById(@PathVariable Long id) {
///     PlanAggregate plan = planService.findById(id)
///         .orElseThrow(() -> new PlanNotFoundException(id));
///     return ApiResponse.ok(PlanResponse.from(plan));
///
/// // GlobalRestExceptionHandler 自动处理
/// // 返回 404 Not Found ProblemDetail
/// {
///   "type": "https://api.patra.com/errors/plan-not-found",
///   "title": "Not Found",
///   "status": 404,
///   "detail": "计划未找到: ID=123",
///   "instance": "/api/plans/123",
///   "traceId": "abc123def456",
///   "timestamp": "2025-01-12T10:30:45.123Z"
/// ```
///
/// ### 参数验证异常处理
///
/// ```java
/// // 请求模型
/// public record CreatePlanRequest(
///     @NotNull(message = "Provenance code 不能为空")
///     ProvenanceCode provenanceCode,
///
///     @NotBlank(message = "External ID 不能为空")
///     @Size(max = 100, message = "External ID 长度不能超过 100")
///     String externalId
/// ) {
///
/// // 控制器
/// @PostMapping("/api/plans")
/// public ApiResponse<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
///     // ...
///
/// // 无效请求
/// POST /api/plans
/// {
///   "externalId": ""
///
/// // GlobalRestExceptionHandler 处理 MethodArgumentNotValidException
/// // 返回 400 Bad Request ProblemDetail
/// {
///   "type": "https://api.patra.com/errors/validation-failed",
///   "title": "Bad Request",
///   "status": 400,
///   "detail": "Validation failed for object='createPlanRequest'",
///   "instance": "/api/plans",
///   "traceId": "xyz789",
///   "errors": [
///     {
///       "field": "provenanceCode",
///       "message": "Provenance code 不能为空",
///     {
///       "field": "externalId",
///       "message": "External ID 不能为空"
///   ]
/// ```
///
/// ## 错误日志
///
/// GlobalRestExceptionHandler 记录详细的错误日志:
///
/// ```
///
/// 2025-01-12 10:30:45.123 [abc123] ERROR GlobalRestExceptionHandler - Exception handled:
///   error code [PLAN_NOT_FOUND],
///   HTTP status 404,
///   request path [/api/plans/123],
///   exception=PlanNotFoundException
/// com.patra.ingest.domain.exception.PlanNotFoundException: 计划未找到: ID=123
///     at com.patra.ingest.app.PlanQueryService.findById(PlanQueryService.java:45)
///     at com.patra.ingest.adapter.rest.PlanController.getById(PlanController.java:78)
///     ...
///
/// ```
///
/// ## 验证错误限制
///
/// 为防止响应体过大,验证错误最多返回 100 个:
///
/// ```java
/// private static final int MAX_VALIDATION_ERRORS = 100;
///
/// if (errors.size() > MAX_VALIDATION_ERRORS) {
///     log.warn("验证错误超出最大限制: total={, 截断为 {",
///         errors.size(), MAX_VALIDATION_ERRORS);
///     return errors.subList(0, MAX_VALIDATION_ERRORS);
/// ```
///
/// ## 优先级控制
///
/// GlobalRestExceptionHandler 使用 `@Order(Ordered.HIGHEST_PRECEDENCE)`, 确保在其他异常处理器之前执行:
///
/// ```java
/// @RestControllerAdvice
/// @Order(Ordered.HIGHEST_PRECEDENCE)
/// public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {
///     // ...
/// ```
///
/// ## 自定义异常处理器
///
/// 如需自定义特定异常的处理逻辑,可创建更高优先级的异常处理器:
///
/// ```java
/// @RestControllerAdvice
/// @Order(Ordered.HIGHEST_PRECEDENCE - 10)  // 比全局处理器优先级更高
/// public class CustomExceptionHandler {
///
///     @ExceptionHandler(MyBusinessException.class)
///     public ResponseEntity<ApiResponse<Void>> handleBusinessException(MyBusinessException ex) {
///         // 使用 ApiResponse 而非 ProblemDetail
///         return ResponseEntity
///             .status(HttpStatus.BAD_REQUEST)
///             .body(ApiResponse.failure(ResultCode.BAD_REQUEST, ex.getMessage()));
/// ```
///
/// ## 内容协商
///
/// GlobalRestExceptionHandler 自动设置正确的 Content-Type:
///
/// ```java
/// return ResponseEntity.status(response.httpStatus())
///     .contentType(MediaType.APPLICATION_PROBLEM_JSON)  // RFC 7807 标准
///     .body(response.problemDetail());
/// ```
///
/// ## 测试支持
///
/// ```java
/// @SpringBootTest
/// @AutoConfigureMockMvc
/// class GlobalRestExceptionHandlerTest {
///
///     @Autowired
///     private MockMvc mockMvc;
///
///     @Test
///     void shouldHandleNotFoundException() throws Exception {
///         mockMvc.perform(get("/api/plans/999"))
///             .andExpect(status().isNotFound())
///             .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
///             .andExpect(jsonPath("$.type").exists())
///             .andExpect(jsonPath("$.status").value(404))
///             .andExpect(jsonPath("$.traceId").exists());
///
///     @Test
///     void shouldHandleValidationException() throws Exception {
///         mockMvc.perform(post("/api/plans")
///                 .contentType(MediaType.APPLICATION_JSON)
///                 .content("{"))
///             .andExpect(status().isBadRequest())
///             .andExpect(jsonPath("$.errors").isArray())
///             .andExpect(jsonPath("$.errors[0].field").exists())
///             .andExpect(jsonPath("$.errors[0].message").exists());
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.web.error.handler;
