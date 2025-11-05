/**
 * Web 请求模型包。
 *
 * <p>本包定义统一的 HTTP 请求模型,提供分页、排序等通用请求参数的标准化封装。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义分页请求模型({@code Pageable})
 *   <li>定义排序请求模型({@code Sortable})
 *   <li>提供分页和排序组合模型({@code PagingSortable})
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.req.Pageable} - 分页请求接口
 *   <li>{@link com.patra.starter.web.req.Sortable} - 排序请求接口
 *   <li>{@link com.patra.starter.web.req.PagingSortable} - 分页排序组合接口
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>分页查询</h3>
 * <pre>{@code
 * public record PlanPageRequest(
 *     int page,         // 页码(从 0 开始)
 *     int size          // 每页大小
 * ) implements Pageable {
 *     // 自动继承 getPage() 和 getSize() 方法
 * }
 *
 * @RestController
 * public class PlanController {
 *     @GetMapping("/api/plans")
 *     public ApiResponse<PageResult<PlanResponse>> listPlans(PlanPageRequest request) {
 *         // 使用分页参数
 *         PageResult<PlanAggregate> plans = planService.findAll(
 *             request.getPage(),
 *             request.getSize()
 *         );
 *
 *         return ApiResponse.ok(PageResult.of(
 *             plans.getItems().stream().map(PlanResponse::from).toList(),
 *             plans.getTotal(),
 *             plans.getPage(),
 *             plans.getSize()
 *         ));
 *     }
 * }
 *
 * // 请求示例
 * GET /api/plans?page=0&size=20
 * }</pre>
 *
 * <h3>排序查询</h3>
 * <pre>{@code
 * public record PlanSortRequest(
 *     String sortBy,        // 排序字段
 *     String sortOrder      // 排序方向(asc/desc)
 * ) implements Sortable {
 *     // 自动继承 getSortBy() 和 getSortOrder() 方法
 * }
 *
 * @GetMapping("/api/plans/sorted")
 * public ApiResponse<List<PlanResponse>> listSortedPlans(PlanSortRequest request) {
 *     List<PlanAggregate> plans = planService.findAllSorted(
 *         request.getSortBy(),
 *         request.getSortOrder()
 *     );
 *     return ApiResponse.ok(plans.stream().map(PlanResponse::from).toList());
 * }
 *
 * // 请求示例
 * GET /api/plans/sorted?sortBy=createdAt&sortOrder=desc
 * }</pre>
 *
 * <h3>分页排序组合</h3>
 * <pre>{@code
 * public record PlanQueryRequest(
 *     int page,
 *     int size,
 *     String sortBy,
 *     String sortOrder
 * ) implements PagingSortable {
 *     // 自动继承分页和排序方法
 * }
 *
 * @GetMapping("/api/plans/query")
 * public ApiResponse<PageResult<PlanResponse>> queryPlans(PlanQueryRequest request) {
 *     PageResult<PlanAggregate> plans = planService.query(
 *         request.getPage(),
 *         request.getSize(),
 *         request.getSortBy(),
 *         request.getSortOrder()
 *     );
 *     return ApiResponse.ok(PageResult.of(
 *         plans.getItems().stream().map(PlanResponse::from).toList(),
 *         plans.getTotal(),
 *         plans.getPage(),
 *         plans.getSize()
 *     ));
 * }
 *
 * // 请求示例
 * GET /api/plans/query?page=0&size=20&sortBy=createdAt&sortOrder=desc
 * }</pre>
 *
 * <h2>默认值推荐</h2>
 *
 * <pre>{@code
 * public record PlanPageRequest(
 *     @Min(0) Integer page,
 *     @Min(1) @Max(100) Integer size
 * ) implements Pageable {
 *
 *     // 提供默认值
 *     public PlanPageRequest {
 *         if (page == null) page = 0;
 *         if (size == null) size = 20;
 *     }
 *
 *     @Override
 *     public int getPage() { return page; }
 *
 *     @Override
 *     public int getSize() { return size; }
 * }
 * }</pre>
 *
 * <h2>参数验证</h2>
 *
 * <pre>{@code
 * public record PlanPageRequest(
 *     @Min(value = 0, message = "页码不能小于 0")
 *     int page,
 *
 *     @Min(value = 1, message = "每页大小不能小于 1")
 *     @Max(value = 100, message = "每页大小不能超过 100")
 *     int size
 * ) implements Pageable {}
 *
 * @GetMapping("/api/plans")
 * public ApiResponse<PageResult<PlanResponse>> listPlans(
 *     @Valid PlanPageRequest request  // 自动验证
 * ) {
 *     // ...
 * }
 *
 * // 无效请求示例
 * GET /api/plans?page=-1&size=200
 *
 * // 自动返回 400 Bad Request
 * {
 *   "type": "about:blank",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Validation failed",
 *   "errors": [
 *     {"field": "page", "message": "页码不能小于 0"},
 *     {"field": "size", "message": "每页大小不能超过 100"}
 *   ]
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>接口隔离</strong> - 分离分页和排序关注点,按需组合
 *   <li><strong>类型安全</strong> - 使用接口约束而非 Map 或原始参数
 *   <li><strong>验证友好</strong> - 配合 JSR-303 注解进行参数验证
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.req;
