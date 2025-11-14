/**
 * Web 响应模型包。
 *
 * <p>本包定义统一的 HTTP 响应模型,提供一致的 API 响应封装和分页结果表示。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义标准 API 响应信封({@code ApiResponse})
 *   <li>定义分页结果模型({@code PageResult})
 *   <li>定义响应结果码枚举({@code ResultCode})
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.resp.ApiResponse} - 统一 API 响应信封
 *   <li>{@link com.patra.starter.web.resp.PageResult} - 分页结果封装
 *   <li>{@link com.patra.starter.web.resp.ResultCode} - 结果码枚举
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>成功响应</h3>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/plans")
 * public class PlanController {
 *
 *     @GetMapping("/{id}")
 *     public ApiResponse<PlanResponse> getById(@PathVariable Long id) {
 *         PlanAggregate plan = planService.findById(id);
 *         return ApiResponse.ok(PlanResponse.from(plan));
 *     }
 * }
 *
 * // 响应示例
 * {
 *   "success": true,
 *   "code": 200,
 *   "message": "OK",
 *   "data": {
 *     "id": 123,
 *     "externalId": "PMC12345",
 *     "provenanceCode": "PUBMED"
 *   },
 *   "timestamp": "2025-01-12T10:30:45.123Z"
 * }
 * }</pre>
 *
 * <h3>业务失败响应</h3>
 *
 * <pre>{@code
 * @PostMapping
 * public ApiResponse<PlanResponse> create(@RequestBody CreatePlanRequest request) {
 *     if (planService.existsByExternalId(request.externalId())) {
 *         return ApiResponse.failure(ResultCode.CONFLICT, "计划已存在");
 *     }
 *
 *     PlanAggregate plan = planService.create(request.toCommand());
 *     return ApiResponse.ok(PlanResponse.from(plan));
 * }
 *
 * // 失败响应示例
 * {
 *   "success": false,
 *   "code": 409,
 *   "message": "计划已存在",
 *   "timestamp": "2025-01-12T10:30:45.123Z"
 * }
 * }</pre>
 *
 * <h3>分页响应</h3>
 *
 * <pre>{@code
 * @GetMapping
 * public ApiResponse<PageResult<PlanResponse>> listPlans(
 *     @RequestParam(defaultValue = "0") int page,
 *     @RequestParam(defaultValue = "20") int size
 * ) {
 *     PageResult<PlanAggregate> plans = planService.findAll(page, size);
 *
 *     PageResult<PlanResponse> response = PageResult.of(
 *         plans.getItems().stream().map(PlanResponse::from).toList(),
 *         plans.getTotal(),
 *         plans.getPage(),
 *         plans.getSize()
 *     );
 *
 *     return ApiResponse.ok(response);
 * }
 *
 * // 响应示例
 * {
 *   "success": true,
 *   "code": 200,
 *   "message": "OK",
 *   "data": {
 *     "items": [
 *       {"id": 1, "externalId": "PMC001", ...},
 *       {"id": 2, "externalId": "PMC002", ...}
 *     ],
 *     "total": 100,
 *     "page": 0,
 *     "size": 20,
 *     "totalPages": 5
 *   },
 *   "timestamp": "2025-01-12T10:30:45.123Z"
 * }
 * }</pre>
 *
 * <h3>错误响应(通过异常处理器)</h3>
 *
 * <pre>{@code
 * // 当抛出异常时,GlobalRestExceptionHandler 自动处理
 * @GetMapping("/{id}")
 * public ApiResponse<PlanResponse> getById(@PathVariable Long id) {
 *     PlanAggregate plan = planService.findById(id)
 *         .orElseThrow(() -> new PlanNotFoundException(id));
 *     return ApiResponse.ok(PlanResponse.from(plan));
 * }
 *
 * // PlanNotFoundException 被全局异常处理器捕获,返回 RFC 7807 ProblemDetail
 * // 而非 ApiResponse
 * }</pre>
 *
 * <h2>响应类型选择</h2>
 *
 * <h3>ApiResponse(推荐用于业务成功/失败)</h3>
 *
 * <ul>
 *   <li><strong>适用场景</strong>: 正常业务流程,包括成功和可预期的业务失败
 *   <li><strong>优势</strong>: 统一的响应格式,前端易于解析
 *   <li><strong>示例</strong>: 创建成功、重复提交、余额不足等
 * </ul>
 *
 * <h3>ProblemDetail(自动用于异常)</h3>
 *
 * <ul>
 *   <li><strong>适用场景</strong>: 系统错误、参数验证失败、资源未找到
 *   <li><strong>优势</strong>: 符合 RFC 7807 标准,包含详细的错误上下文
 *   <li><strong>示例</strong>: 404 Not Found、400 Bad Request、500 Internal Server Error
 * </ul>
 *
 * <h2>ResultCode 枚举</h2>
 *
 * <pre>{@code
 * public enum ResultCode {
 *     OK(200, "OK"),
 *     CREATED(201, "Created"),
 *     BAD_REQUEST(400, "Bad Request"),
 *     UNAUTHORIZED(401, "Unauthorized"),
 *     FORBIDDEN(403, "Forbidden"),
 *     NOT_FOUND(404, "Not Found"),
 *     CONFLICT(409, "Conflict"),
 *     INTERNAL_SERVER_ERROR(500, "Internal Server Error");
 *
 *     // ...
 * }
 * }</pre>
 *
 * <h2>最佳实践</h2>
 *
 * <ol>
 *   <li><strong>成功场景</strong> - 使用 {@code ApiResponse.ok(data)}
 *   <li><strong>可预期业务失败</strong> - 使用 {@code ApiResponse.failure(ResultCode, message)}
 *   <li><strong>系统错误/验证失败</strong> - 抛异常,由全局异常处理器转换为 ProblemDetail
 *   <li><strong>分页查询</strong> - 使用 {@code PageResult.of(...)} 封装分页数据
 *   <li><strong>空数据</strong> - 返回 {@code ApiResponse.ok(null)} 或 {@code
 *       ApiResponse.ok(Collections.emptyList())}
 * </ol>
 *
 * <h2>JSON 序列化配置</h2>
 *
 * <pre>{@code
 * spring:
 *   jackson:
 *     default-property-inclusion: non_null  # 忽略 null 字段
 *     serialization:
 *       write-dates-as-timestamps: false    # 使用 ISO-8601 日期格式
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.resp;
