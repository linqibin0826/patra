/// Web 错误处理工具包。
/// 
/// 本包提供 Web 错误处理过程中使用的工具类,简化常见操作。
/// 
/// ## 职责
/// 
/// - 提供 HTTP 状态码转换工具
///   - 提供错误响应构建辅助方法
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.web.error.util.HttpStatusConverter} - HTTP 状态码转换器
/// 
/// ## HttpStatusConverter 工具
/// 
/// 提供 int 状态码到 Spring `HttpStatus` 枚举的转换:
/// 
/// ```java
/// // int → HttpStatus
/// HttpStatus status = HttpStatusConverter.valueOf(404);
/// // 返回: HttpStatus.NOT_FOUND
/// 
/// // 处理非标准状态码
/// HttpStatus custom = HttpStatusConverter.valueOf(599);
/// // 返回: HttpStatus.valueOf(599)
/// ```
/// 
/// ## 使用场景
/// 
/// ### 在适配器中使用
/// 
/// ```java
/// @Component
/// public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {
/// 
///     @Override
///     public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
///         ErrorResolution resolution = pipeline.resolve(exception);
/// 
///         // 使用 HttpStatusConverter 转换状态码
///         HttpStatus httpStatus = HttpStatusConverter.valueOf(resolution.getHttpStatus());
/// 
///         ProblemDetail problemDetail = ProblemDetail.forStatus(httpStatus);
///         // ...
/// 
///         return new ProblemDetailResponse(problemDetail, resolution, httpStatus);
/// ```
/// 
/// ### 标准 HTTP 状态码
/// 
/// ```java
/// // 2xx Success
/// HttpStatusConverter.valueOf(200);  // OK
/// HttpStatusConverter.valueOf(201);  // CREATED
/// HttpStatusConverter.valueOf(204);  // NO_CONTENT
/// 
/// // 4xx Client Error
/// HttpStatusConverter.valueOf(400);  // BAD_REQUEST
/// HttpStatusConverter.valueOf(401);  // UNAUTHORIZED
/// HttpStatusConverter.valueOf(403);  // FORBIDDEN
/// HttpStatusConverter.valueOf(404);  // NOT_FOUND
/// HttpStatusConverter.valueOf(409);  // CONFLICT
/// 
/// // 5xx Server Error
/// HttpStatusConverter.valueOf(500);  // INTERNAL_SERVER_ERROR
/// HttpStatusConverter.valueOf(503);  // SERVICE_UNAVAILABLE
/// ```
/// 
/// ### 自定义状态码
/// 
/// ```java
/// // 非标准状态码(Spring 支持自定义状态码)
/// HttpStatus custom = HttpStatusConverter.valueOf(599);
/// System.out.println(custom.value());        // 599
/// System.out.println(custom.getReasonPhrase());  // "Unknown"
/// ```
/// 
/// ## 与 Spring HttpStatus 的关系
/// 
/// HttpStatusConverter 是对 Spring `HttpStatus` 的薄封装:
/// 
/// ```java
/// public class HttpStatusConverter {
///     public static HttpStatus valueOf(int statusCode) {
///         // 委托给 Spring 的 HttpStatus.valueOf()
///         return HttpStatus.valueOf(statusCode);
/// ```
/// 
/// ## 错误处理
/// 
/// ```java
/// try {
///     HttpStatus status = HttpStatusConverter.valueOf(-1); catch (IllegalArgumentException e) {
///     // 无效的状态码(< 100 或 > 599)
///     log.error("Invalid HTTP status code: -1", e);
/// ```
/// 
/// ## 常用状态码快速参考
/// 
/// <table border="1">
///   <tr>
///     <th>状态码</th>
///     <th>Spring 枚举</th>
///     <th>描述</th>
///     <th>使用场景</th>
///   </tr>
///   <tr>
///     <td>200</td>
///     <td>OK</td>
///     <td>请求成功</td>
///     <td>查询、更新成功</td>
///   </tr>
///   <tr>
///     <td>201</td>
///     <td>CREATED</td>
///     <td>资源创建成功</td>
///     <td>POST 创建资源</td>
///   </tr>
///   <tr>
///     <td>204</td>
///     <td>NO_CONTENT</td>
///     <td>成功但无返回内容</td>
///     <td>DELETE 删除成功</td>
///   </tr>
///   <tr>
///     <td>400</td>
///     <td>BAD_REQUEST</td>
///     <td>请求参数错误</td>
///     <td>参数验证失败</td>
///   </tr>
///   <tr>
///     <td>401</td>
///     <td>UNAUTHORIZED</td>
///     <td>未认证</td>
///     <td>未登录或 Token 过期</td>
///   </tr>
///   <tr>
///     <td>403</td>
///     <td>FORBIDDEN</td>
///     <td>无权限</td>
///     <td>已登录但权限不足</td>
///   </tr>
///   <tr>
///     <td>404</td>
///     <td>NOT_FOUND</td>
///     <td>资源不存在</td>
///     <td>查询资源未找到</td>
///   </tr>
///   <tr>
///     <td>409</td>
///     <td>CONFLICT</td>
///     <td>资源冲突</td>
///     <td>重复创建、并发冲突</td>
///   </tr>
///   <tr>
///     <td>500</td>
///     <td>INTERNAL_SERVER_ERROR</td>
///     <td>服务器内部错误</td>
///     <td>未预期的异常</td>
///   </tr>
///   <tr>
///     <td>503</td>
///     <td>SERVICE_UNAVAILABLE</td>
///     <td>服务不可用</td>
///     <td>熔断、限流、维护</td>
///   </tr>
/// </table>
/// 
/// ## 最佳实践
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.web.error.util;
