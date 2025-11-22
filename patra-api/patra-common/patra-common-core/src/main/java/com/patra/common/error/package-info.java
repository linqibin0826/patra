/// 异常处理框架包 - 领域异常和应用异常基类。
///
/// 本包提供 Patra 平台统一的异常体系,包括领域异常基类、应用异常基类、错误特征枚举和错误码接口。 异常体系支持六边形架构的异常映射,将领域异常转换为适配器层的 HTTP
/// ProblemDetail 响应。
///
/// ## 职责
///
/// - 定义领域层异常基类({@link com.patra.common.error.DomainException})
///   - 定义应用层异常基类({@link com.patra.common.error.ApplicationException})
///   - 提供错误特征枚举({@link com.patra.common.error.trait.ErrorTrait})
///   - 定义错误码接口({@link com.patra.common.error.codes.ErrorCodeLike})
///   - 支持异常到 HTTP 状态码的映射
///   - 保持领域层的框架无关性
///
/// ## 子包结构
///
/// - `com.patra.common.error.trait` - 错误特征枚举和接口
///
/// - {@link com.patra.common.error.trait.ErrorTrait} - 语义化错误分类(NOT_FOUND、CONFLICT 等)
///         - {@link com.patra.common.error.trait.HasErrorTraits} - 错误特征标记接口
///
///   - `com.patra.common.error.codes` - 错误码接口和标准错误码
///
/// - {@link com.patra.common.error.codes.ErrorCodeLike} - 错误码接口
///         - {@link com.patra.common.error.codes.HttpStdErrors} - 标准 HTTP 错误码生成器
///
///   - `com.patra.common.error.problem` - ProblemDetail 相关常量
///
/// - {@link com.patra.common.error.problem.ErrorKeys} - 错误键常量
///
/// ## 核心异常类
///
/// - {@link com.patra.common.error.DomainException} - 领域层异常基类, 表示业务规则违反和领域不变量破坏,保持框架无关性
///   - {@link com.patra.common.error.ApplicationException} - 应用层异常基类, 表示应用层错误和编排失败
///
/// ## 错误特征枚举
///
/// {@link com.patra.common.error.trait.ErrorTrait} 提供语义化错误分类, 用于将异常映射到 HTTP 状态码:
///
/// - `NOT_FOUND` → HTTP 404
///   - `CONFLICT` → HTTP 409
///   - `RULE_VIOLATION` → HTTP 422
///   - `QUOTA_EXCEEDED` → HTTP 429
///   - `UNAUTHORIZED` → HTTP 401
///   - `FORBIDDEN` → HTTP 403
///   - `TIMEOUT` → HTTP 504
///   - `DEP_UNAVAILABLE` → HTTP 503
///
/// ## 使用场景
///
/// - **领域层**: 继承 {@link com.patra.common.error.DomainException} 定义业务异常
///   - **应用层**: 继承 {@link com.patra.common.error.ApplicationException} 定义应用异常
///   - **适配器层**: 将异常映射为 HTTP ProblemDetail 响应
///   - **错误码管理**: 使用 {@link com.patra.common.error.codes.ErrorCodeLike} 定义服务特定错误码
///
/// ## 使用示例
///
/// ```java
/// // 1. 定义领域异常
/// public class PublicationNotFoundException extends DomainException
///     implements HasErrorTraits {
///
///     public PublicationNotFoundException(String publicationId) {
///         super("出版物不存在: " + publicationId);
///
///     @Override
///     public Set<ErrorTrait> getErrorTraits() {
///         return Set.of(ErrorTrait.NOT_FOUND);
///
/// // 2. 定义应用异常
/// public class IngestQuotaExceededException extends ApplicationException
///     implements HasErrorTraits {
///
///     public IngestQuotaExceededException(String message) {
///         super(message);
///
///     @Override
///     public Set<ErrorTrait> getErrorTraits() {
///         return Set.of(ErrorTrait.QUOTA_EXCEEDED);
///
/// // 3. 适配器层映射为 HTTP 响应
/// @RestControllerAdvice
/// public class GlobalExceptionHandler {
///     @ExceptionHandler(PublicationNotFoundException.class)
///     public ProblemDetail handleNotFound(PublicationNotFoundException ex) {
///         ProblemDetail problem = ProblemDetail.forStatusAndDetail(
///             HttpStatus.NOT_FOUND,
///             ex.getMessage()
///         );
///         // 添加错误码等扩展字段
///         return problem;
/// ```
///
/// ## 异常层次结构
///
/// ```
///
/// RuntimeException
///   ├── DomainException (领域层异常基类)
///   │     ├── PublicationNotFoundException
///   │     ├── RegistryException
///   │     └── ... (各服务的领域异常)
///   │
///   └── ApplicationException (应用层异常基类)
///         ├── IngestQuotaExceededException
///         └── ... (各服务的应用异常)
///
/// ```
///
/// ## 设计原则
///
/// - **框架无关**: 领域异常不依赖 Spring 或其他框架
///   - **语义明确**: 异常名称清晰表达业务含义
///   - **映射友好**: 通过 {@link com.patra.common.error.trait.ErrorTrait} 简化 HTTP 状态码映射
///   - **快速失败**: 在领域对象构造时立即抛出验证异常
///
/// @since 0.1.0
/// @author linqibin
package com.patra.common.error;
