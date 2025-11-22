/// Web 响应模型包。
/// 
/// 本包定义统一的 HTTP 响应模型,提供一致的 API 响应封装和分页结果表示。
/// 
/// ## 职责
/// 
/// - 定义标准 API 响应信封(`ApiResponse`)
///   - 定义分页结果模型(`PageResult`)
///   - 定义响应结果码枚举(`ResultCode`)
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.web.resp.ApiResponse} - 统一 API 响应信封
///   - {@link com.patra.starter.web.resp.PageResult} - 分页结果封装
///   - {@link com.patra.starter.web.resp.ResultCode} - 结果码枚举
/// 
/// ## 使用示例
/// 
/// ### 成功响应
/// 
/// ```java
/// @RestController
/// @RequestMapping("/api/plans")
/// public class PlanController {
/// 
///     @GetMapping("/{id")
///     public ApiResponse<PlanResponse> getById(@PathVariable Long id) {
///         PlanAggregate plan = planService.findById(id);
///         return ApiResponse.ok(PlanResponse.from(plan));
/// 
/// // 响应示例
/// {
///   "success": true,
///   "code": 200,
///   "message": "OK",
///   "data": {
///     "id": 123,
///     "externalId": "PMC12345",
///     "provenanceCode": "PUBMED",
///   "timestamp": "2025-01-12T10:30:45.123Z"
/// ```
/// 
/// ### 业务失败响应
/// 
/// ```java
/// @PostMapping
/// public ApiResponse<PlanResponse> create(@RequestBody CreatePlanRequest request) {
///     if (planService.existsByExternalId(request.externalId())) {
///         return ApiResponse.failure(ResultCode.CONFLICT, "计划已存在");
/// 
///     PlanAggregate plan = planService.create(request.toCommand());
///     return ApiResponse.ok(PlanResponse.from(plan));
/// 
/// // 失败响应示例
/// {
///   "success": false,
///   "code": 409,
///   "message": "计划已存在",
///   "timestamp": "2025-01-12T10:30:45.123Z"
/// ```
/// 
/// ### 分页响应
/// 
/// ```java
/// @GetMapping
/// public ApiResponse<PageResult<PlanResponse>> listPlans(
///     @RequestParam(defaultValue = "0") int page,
///     @RequestParam(defaultValue = "20") int size
/// ) {
///     PageResult<PlanAggregate> plans = planService.findAll(page, size);
/// 
///     PageResult<PlanResponse> response = PageResult.of(
///         plans.getItems().stream().map(PlanResponse::from).toList(),
///         plans.getTotal(),
///         plans.getPage(),
///         plans.getSize()
///     );
/// 
///     return ApiResponse.ok(response);
/// 
/// // 响应示例
/// {
///   "success": true,
///   "code": 200,
///   "message": "OK",
///   "data": {
///     "items": [
///       {"id": 1, "externalId": "PMC001", ...,
///       {"id": 2, "externalId": "PMC002", ...
///     ],
///     "total": 100,
///     "page": 0,
///     "size": 20,
///     "totalPages": 5,
///   "timestamp": "2025-01-12T10:30:45.123Z"
/// ```
/// 
/// ### 错误响应(通过异常处理器)
/// 
/// ```java
/// // 当抛出异常时,GlobalRestExceptionHandler 自动处理
/// @GetMapping("/{id")
/// public ApiResponse<PlanResponse> getById(@PathVariable Long id) {
///     PlanAggregate plan = planService.findById(id)
///         .orElseThrow(() -> new PlanNotFoundException(id));
///     return ApiResponse.ok(PlanResponse.from(plan));
/// 
/// // PlanNotFoundException 被全局异常处理器捕获,返回 RFC 7807 ProblemDetail
/// // 而非 ApiResponse
/// ```
/// 
/// ## 响应类型选择
/// 
/// ### ApiResponse(推荐用于业务成功/失败)
/// 
/// - **适用场景**: 正常业务流程,包括成功和可预期的业务失败
///   - **优势**: 统一的响应格式,前端易于解析
///   - **示例**: 创建成功、重复提交、余额不足等
/// 
/// ### ProblemDetail(自动用于异常)
/// 
/// - **适用场景**: 系统错误、参数验证失败、资源未找到
///   - **优势**: 符合 RFC 7807 标准,包含详细的错误上下文
///   - **示例**: 404 Not Found、400 Bad Request、500 Internal Server Error
/// 
/// ## ResultCode 枚举
/// 
/// ```java
/// public enum ResultCode {
///     OK(200, "OK"),
///     CREATED(201, "Created"),
///     BAD_REQUEST(400, "Bad Request"),
///     UNAUTHORIZED(401, "Unauthorized"),
///     FORBIDDEN(403, "Forbidden"),
///     NOT_FOUND(404, "Not Found"),
///     CONFLICT(409, "Conflict"),
///     INTERNAL_SERVER_ERROR(500, "Internal Server Error");
/// 
///     // ...
/// ```
/// 
/// ## 最佳实践
/// 
/// ## JSON 序列化配置
/// 
/// ```java
/// spring:
///   jackson:
///     default-property-inclusion: non_null  # 忽略 null 字段
///     serialization:
///       write-dates-as-timestamps: false    # 使用 ISO-8601 日期格式
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.web.resp;
