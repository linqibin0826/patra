/// Registry 领域异常包 - 业务规则违反和领域错误定义。
/// 
/// 本包包含 Registry 服务领域层的异常定义,表示业务规则违反、领域不变量破坏和领域特定错误。 所有异常都继承自运行时异常,遵循"快速失败"原则,在领域对象构造和业务操作时立即抛出。
/// 
/// ## 职责
/// 
/// - 定义领域层的异常层次结构
///   - 表示业务规则违反和领域不变量破坏
///   - 提供语义化的验证工具方法
///   - 支持适配器层的异常映射(转换为 HTTP 状态码和 ProblemDetail)
///   - 保持领域层的框架无关性(不依赖 Spring 等)
/// 
/// ## 核心异常
/// 
/// - {@link com.patra.registry.domain.exception.RegistryException} - Registry 服务领域异常基类
///   - {@link com.patra.registry.domain.exception.DomainValidationException} - 领域验证异常, 用于替代 {@link
///       IllegalArgumentException},提供便捷的验证工具方法
///   - {@link com.patra.registry.domain.exception.RegistryNotFound} - 资源未找到异常, 映射为 HTTP 404
///   - {@link com.patra.registry.domain.exception.RegistryConflict} - 资源冲突异常, 映射为 HTTP 409
///   - {@link com.patra.registry.domain.exception.RegistryRuleViolation} - 业务规则违反异常, 映射为 HTTP 422
///   - {@link com.patra.registry.domain.exception.RegistryQuotaExceeded} - 配额超限异常, 映射为 HTTP 429
/// 
/// ## 异常层次结构
/// 
/// ```
/// 
/// RuntimeException
///   └── RegistryException (Registry 领域异常基类)
///         ├── DomainValidationException (验证异常,含工具方法)
///         ├── RegistryNotFound (资源未找到)
///         ├── RegistryConflict (资源冲突)
///         ├── RegistryRuleViolation (业务规则违反)
///         └── RegistryQuotaExceeded (配额超限)
/// 
/// ```
/// 
/// ## 验证工具方法
/// 
/// {@link com.patra.registry.domain.exception.DomainValidationException} 提供了便捷的验证工具方法:
/// 
/// - `notBlank(value, field)` - 验证字符串非空且非空白,返回 trim 后的值
///   - `nonNull(obj, field)` - 验证对象非 null
///   - `positive(number, field)` - 验证数字为正数(> 0)
///   - `nonNegative(number, field)` - 验证数字非负(>= 0)
///   - `withinRange(value, min, max, field)` - 验证值在指定范围内
///   - `require(condition, message)` - 条件验证,类似断言
/// 
/// ## 使用场景
/// 
/// - **值对象构造**: 在 `record` 的规范构造器中验证业务约束
///   - **聚合根方法**: 在业务操作前验证前置条件
///   - **领域服务**: 验证跨聚合的业务规则
///   - **仓储查询**: 验证查询参数的有效性
/// 
/// ## 使用示例
/// 
/// ```java
/// // 在值对象构造器中使用验证方法
/// public record Provenance(Long id, String code, String name, ...) {
///     public Provenance {
///         DomainValidationException.positive(id, "Provenance id");
///         String codeTrimmed = DomainValidationException.notBlank(code, "Provenance code");
///         String nameTrimmed = DomainValidationException.notBlank(name, "Provenance name");
/// 
///         this.id = id;
///         this.code = codeTrimmed;
///         this.name = nameTrimmed;
///         // ...
/// 
/// // 在聚合根方法中验证业务规则
/// public void updateConfiguration(HttpConfig newConfig) {
///     DomainValidationException.nonNull(newConfig, "HTTP config");
///     if (!this.provenance.isActive()) {
///         throw new RegistryRuleViolation("Cannot update inactive provenance");
///     // ...
/// 
/// // 抛出资源未找到异常
/// public Provenance getProvenance(ProvenanceCode code) {
///     return repository.findProvenanceByCode(code)
///         .orElseThrow(() -> new RegistryNotFound("Provenance not found: " + code));
/// ```
/// 
/// ## 适配器层映射
/// 
/// 适配器层(`patra-registry-adapter`)负责将领域异常映射为 HTTP 响应:
/// 
/// - {@link com.patra.registry.domain.exception.DomainValidationException} → HTTP 400 Bad
///       Request
///   - {@link com.patra.registry.domain.exception.RegistryNotFound} → HTTP 404 Not Found
///   - {@link com.patra.registry.domain.exception.RegistryConflict} → HTTP 409 Conflict
///   - {@link com.patra.registry.domain.exception.RegistryRuleViolation} → HTTP 422 Unprocessable
///       Entity
///   - {@link com.patra.registry.domain.exception.RegistryQuotaExceeded} → HTTP 429 Too Many
///       Requests
/// 
/// ## 设计原则
/// 
/// - **快速失败**: 在领域对象构造时立即验证,不允许创建无效对象
///   - **框架无关**: 不依赖 Spring、Jakarta EE 等框架,保持领域层纯粹性
///   - **语义明确**: 异常名称清晰表达业务含义,便于理解和映射
///   - **工具优先**: 优先使用 {@link
///       com.patra.registry.domain.exception.DomainValidationException} 的工具方法,避免重复代码
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.domain.exception;
