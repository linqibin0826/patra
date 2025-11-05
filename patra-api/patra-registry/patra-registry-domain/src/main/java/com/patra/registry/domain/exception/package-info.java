/**
 * Registry 领域异常包 - 业务规则违反和领域错误定义。
 *
 * <p>本包包含 Registry 服务领域层的异常定义,表示业务规则违反、领域不变量破坏和领域特定错误。
 * 所有异常都继承自运行时异常,遵循"快速失败"原则,在领域对象构造和业务操作时立即抛出。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义领域层的异常层次结构
 *   <li>表示业务规则违反和领域不变量破坏
 *   <li>提供语义化的验证工具方法
 *   <li>支持适配器层的异常映射(转换为 HTTP 状态码和 ProblemDetail)
 *   <li>保持领域层的框架无关性(不依赖 Spring 等)
 * </ul>
 *
 * <h2>核心异常</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.exception.RegistryException} - Registry 服务领域异常基类
 *   <li>{@link com.patra.registry.domain.exception.DomainValidationException} - 领域验证异常,
 *       用于替代 {@link IllegalArgumentException},提供便捷的验证工具方法
 *   <li>{@link com.patra.registry.domain.exception.RegistryNotFound} - 资源未找到异常,
 *       映射为 HTTP 404
 *   <li>{@link com.patra.registry.domain.exception.RegistryConflict} - 资源冲突异常,
 *       映射为 HTTP 409
 *   <li>{@link com.patra.registry.domain.exception.RegistryRuleViolation} - 业务规则违反异常,
 *       映射为 HTTP 422
 *   <li>{@link com.patra.registry.domain.exception.RegistryQuotaExceeded} - 配额超限异常,
 *       映射为 HTTP 429
 * </ul>
 *
 * <h2>异常层次结构</h2>
 *
 * <pre>
 * RuntimeException
 *   └── RegistryException (Registry 领域异常基类)
 *         ├── DomainValidationException (验证异常,含工具方法)
 *         ├── RegistryNotFound (资源未找到)
 *         ├── RegistryConflict (资源冲突)
 *         ├── RegistryRuleViolation (业务规则违反)
 *         └── RegistryQuotaExceeded (配额超限)
 * </pre>
 *
 * <h2>验证工具方法</h2>
 *
 * <p>{@link com.patra.registry.domain.exception.DomainValidationException} 提供了便捷的验证工具方法:
 *
 * <ul>
 *   <li>{@code notBlank(value, field)} - 验证字符串非空且非空白,返回 trim 后的值
 *   <li>{@code nonNull(obj, field)} - 验证对象非 null
 *   <li>{@code positive(number, field)} - 验证数字为正数(> 0)
 *   <li>{@code nonNegative(number, field)} - 验证数字非负(>= 0)
 *   <li>{@code withinRange(value, min, max, field)} - 验证值在指定范围内
 *   <li>{@code require(condition, message)} - 条件验证,类似断言
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>值对象构造</strong>: 在 {@code record} 的规范构造器中验证业务约束
 *   <li><strong>聚合根方法</strong>: 在业务操作前验证前置条件
 *   <li><strong>领域服务</strong>: 验证跨聚合的业务规则
 *   <li><strong>仓储查询</strong>: 验证查询参数的有效性
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 在值对象构造器中使用验证方法
 * public record Provenance(Long id, String code, String name, ...) {
 *     public Provenance {
 *         DomainValidationException.positive(id, "Provenance id");
 *         String codeTrimmed = DomainValidationException.notBlank(code, "Provenance code");
 *         String nameTrimmed = DomainValidationException.notBlank(name, "Provenance name");
 *
 *         this.id = id;
 *         this.code = codeTrimmed;
 *         this.name = nameTrimmed;
 *         // ...
 *     }
 * }
 *
 * // 在聚合根方法中验证业务规则
 * public void updateConfiguration(HttpConfig newConfig) {
 *     DomainValidationException.nonNull(newConfig, "HTTP config");
 *     if (!this.provenance.isActive()) {
 *         throw new RegistryRuleViolation("Cannot update inactive provenance");
 *     }
 *     // ...
 * }
 *
 * // 抛出资源未找到异常
 * public Provenance getProvenance(ProvenanceCode code) {
 *     return repository.findProvenanceByCode(code)
 *         .orElseThrow(() -> new RegistryNotFound("Provenance not found: " + code));
 * }
 * }</pre>
 *
 * <h2>适配器层映射</h2>
 *
 * <p>适配器层({@code patra-registry-adapter})负责将领域异常映射为 HTTP 响应:
 *
 * <ul>
 *   <li>{@link com.patra.registry.domain.exception.DomainValidationException} → HTTP 400 Bad Request
 *   <li>{@link com.patra.registry.domain.exception.RegistryNotFound} → HTTP 404 Not Found
 *   <li>{@link com.patra.registry.domain.exception.RegistryConflict} → HTTP 409 Conflict
 *   <li>{@link com.patra.registry.domain.exception.RegistryRuleViolation} → HTTP 422 Unprocessable Entity
 *   <li>{@link com.patra.registry.domain.exception.RegistryQuotaExceeded} → HTTP 429 Too Many Requests
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>快速失败</strong>: 在领域对象构造时立即验证,不允许创建无效对象
 *   <li><strong>框架无关</strong>: 不依赖 Spring、Jakarta EE 等框架,保持领域层纯粹性
 *   <li><strong>语义明确</strong>: 异常名称清晰表达业务含义,便于理解和映射
 *   <li><strong>工具优先</strong>: 优先使用 {@link com.patra.registry.domain.exception.DomainValidationException} 的工具方法,避免重复代码
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.domain.exception;
