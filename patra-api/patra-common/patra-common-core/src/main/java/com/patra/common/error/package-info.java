/**
 * 异常处理框架包 - 领域异常和应用异常基类。
 *
 * <p>本包提供 Patra 平台统一的异常体系,包括领域异常基类、应用异常基类、错误特征枚举和错误码接口。
 * 异常体系支持六边形架构的异常映射,将领域异常转换为适配器层的 HTTP ProblemDetail 响应。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义领域层异常基类({@link com.patra.common.error.DomainException})
 *   <li>定义应用层异常基类({@link com.patra.common.error.ApplicationException})
 *   <li>提供错误特征枚举({@link com.patra.common.error.trait.ErrorTrait})
 *   <li>定义错误码接口({@link com.patra.common.error.codes.ErrorCodeLike})
 *   <li>支持异常到 HTTP 状态码的映射
 *   <li>保持领域层的框架无关性
 * </ul>
 *
 * <h2>子包结构</h2>
 *
 * <ul>
 *   <li>{@code com.patra.common.error.trait} - 错误特征枚举和接口
 *       <ul>
 *         <li>{@link com.patra.common.error.trait.ErrorTrait} - 语义化错误分类(NOT_FOUND、CONFLICT 等)
 *         <li>{@link com.patra.common.error.trait.HasErrorTraits} - 错误特征标记接口
 *       </ul>
 *   <li>{@code com.patra.common.error.codes} - 错误码接口和标准错误码
 *       <ul>
 *         <li>{@link com.patra.common.error.codes.ErrorCodeLike} - 错误码接口
 *         <li>{@link com.patra.common.error.codes.HttpStdErrors} - 标准 HTTP 错误码生成器
 *       </ul>
 *   <li>{@code com.patra.common.error.problem} - ProblemDetail 相关常量
 *       <ul>
 *         <li>{@link com.patra.common.error.problem.ErrorKeys} - 错误键常量
 *       </ul>
 * </ul>
 *
 * <h2>核心异常类</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.error.DomainException} - 领域层异常基类,
 *       表示业务规则违反和领域不变量破坏,保持框架无关性
 *   <li>{@link com.patra.common.error.ApplicationException} - 应用层异常基类,
 *       表示应用层错误和编排失败
 * </ul>
 *
 * <h2>错误特征枚举</h2>
 *
 * <p>{@link com.patra.common.error.trait.ErrorTrait} 提供语义化错误分类,
 * 用于将异常映射到 HTTP 状态码:
 *
 * <ul>
 *   <li>{@code NOT_FOUND} → HTTP 404
 *   <li>{@code CONFLICT} → HTTP 409
 *   <li>{@code RULE_VIOLATION} → HTTP 422
 *   <li>{@code QUOTA_EXCEEDED} → HTTP 429
 *   <li>{@code UNAUTHORIZED} → HTTP 401
 *   <li>{@code FORBIDDEN} → HTTP 403
 *   <li>{@code TIMEOUT} → HTTP 504
 *   <li>{@code DEP_UNAVAILABLE} → HTTP 503
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>领域层</strong>: 继承 {@link com.patra.common.error.DomainException} 定义业务异常
 *   <li><strong>应用层</strong>: 继承 {@link com.patra.common.error.ApplicationException} 定义应用异常
 *   <li><strong>适配器层</strong>: 将异常映射为 HTTP ProblemDetail 响应
 *   <li><strong>错误码管理</strong>: 使用 {@link com.patra.common.error.codes.ErrorCodeLike} 定义服务特定错误码
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 定义领域异常
 * public class LiteratureNotFoundException extends DomainException
 *     implements HasErrorTraits {
 *
 *     public LiteratureNotFoundException(String literatureId) {
 *         super("文献不存在: " + literatureId);
 *     }
 *
 *     @Override
 *     public Set<ErrorTrait> getErrorTraits() {
 *         return Set.of(ErrorTrait.NOT_FOUND);
 *     }
 * }
 *
 * // 2. 定义应用异常
 * public class IngestQuotaExceededException extends ApplicationException
 *     implements HasErrorTraits {
 *
 *     public IngestQuotaExceededException(String message) {
 *         super(message);
 *     }
 *
 *     @Override
 *     public Set<ErrorTrait> getErrorTraits() {
 *         return Set.of(ErrorTrait.QUOTA_EXCEEDED);
 *     }
 * }
 *
 * // 3. 适配器层映射为 HTTP 响应
 * @RestControllerAdvice
 * public class GlobalExceptionHandler {
 *     @ExceptionHandler(LiteratureNotFoundException.class)
 *     public ProblemDetail handleNotFound(LiteratureNotFoundException ex) {
 *         ProblemDetail problem = ProblemDetail.forStatusAndDetail(
 *             HttpStatus.NOT_FOUND,
 *             ex.getMessage()
 *         );
 *         // 添加错误码等扩展字段
 *         return problem;
 *     }
 * }
 * }</pre>
 *
 * <h2>异常层次结构</h2>
 *
 * <pre>
 * RuntimeException
 *   ├── DomainException (领域层异常基类)
 *   │     ├── LiteratureNotFoundException
 *   │     ├── RegistryException
 *   │     └── ... (各服务的领域异常)
 *   │
 *   └── ApplicationException (应用层异常基类)
 *         ├── IngestQuotaExceededException
 *         └── ... (各服务的应用异常)
 * </pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>框架无关</strong>: 领域异常不依赖 Spring 或其他框架
 *   <li><strong>语义明确</strong>: 异常名称清晰表达业务含义
 *   <li><strong>映射友好</strong>: 通过 {@link com.patra.common.error.trait.ErrorTrait} 简化 HTTP 状态码映射
 *   <li><strong>快速失败</strong>: 在领域对象构造时立即抛出验证异常
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common.error;
