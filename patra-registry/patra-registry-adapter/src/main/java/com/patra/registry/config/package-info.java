/// Registry 适配器配置包 - Spring Boot 配置和 SPI 实现。
///
/// 本包包含 Registry 服务适配器层的 Spring Boot 配置类、异常映射贡献者和其他基础设施配置。配置类负责装配适配器层的 Bean,并提供领域异常到 HTTP
/// 错误码的映射规则。
///
/// ## 职责
///
/// - 提供 Spring Boot 自动配置和 Bean 装配
///   - 实现错误映射 SPI,将领域异常映射为 HTTP 错误码
///   - 配置适配器层的横切关注点(日志、监控等)
///   - 注册自定义转换器和格式化器
///
/// ## 核心组件
///
/// - {@link com.patra.registry.config.RegistryErrorMappingContributor} - Registry 异常映射贡献者
///
/// - 将 `DomainValidationException` 映射为 `BAD_REQUEST`
///         - 将 `RegistryQuotaExceeded` 映射为 `CONFLICT`
///         - 将 `DuplicateKeyException` 映射为 `CONFLICT`
///         - 将 `DataIntegrityViolationException` 映射为 `UNPROCESSABLE`
///         - 将 `OptimisticLockingFailureException` 映射为 `CONFLICT`
///
/// ## 设计原则
///
/// - **SPI 机制**: 通过 `ErrorMappingContributor` 接口扩展全局异常映射
///   - **显式映射**: 为领域异常提供精确的 HTTP 错误码映射
///   - **从 boot 迁移**: 配置从 boot 模块迁移到 adapter,避免 boot 直接依赖 domain/api
///
/// ## 错误映射规则
///
/// <table border="1">
///   <caption>Registry 异常到 HTTP 错误码映射表</caption>
///   <tr>
///     <th>异常类型</th>
///     <th>HTTP 状态码</th>
///     <th>错误码前缀</th>
///     <th>说明</th>
///   </tr>
///   <tr>
///     <td>`DomainValidationException`</td>
///     <td>400</td>
///     <td>REG-04xx</td>
///     <td>领域验证失败</td>
///   </tr>
///   <tr>
///     <td>`RegistryQuotaExceeded`</td>
///     <td>409</td>
///     <td>REG-09xx</td>
///     <td>配额超限</td>
///   </tr>
///   <tr>
///     <td>`DuplicateKeyException`</td>
///     <td>409</td>
///     <td>REG-09xx</td>
///     <td>唯一键冲突</td>
///   </tr>
///   <tr>
///     <td>`DataIntegrityViolationException`</td>
///     <td>422</td>
///     <td>REG-22xx</td>
///     <td>数据完整性违反</td>
///   </tr>
///   <tr>
///     <td>`OptimisticLockingFailureException`</td>
///     <td>409</td>
///     <td>REG-09xx</td>
///     <td>乐观锁冲突</td>
///   </tr>
/// </table>
///
/// ## 使用示例
///
/// ```java
/// @Component
/// public class RegistryErrorMappingContributor implements ErrorMappingContributor {
///     private final HttpStdErrors.Group http;
///
///     @Override
///     public Optional<ErrorCodeLike> mapException(Throwable exception) {
///         if (exception instanceof DomainValidationException) {
///             return Optional.of(http.BAD_REQUEST());
///         // ...其他映射规则
///         return Optional.empty();
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.config;
