/**
 * Registry 适配器配置包 - Spring Boot 配置和 SPI 实现。
 *
 * <p>本包包含 Registry 服务适配器层的 Spring Boot 配置类、异常映射贡献者和其他基础设施配置。配置类负责装配适配器层的 Bean,并提供领域异常到 HTTP
 * 错误码的映射规则。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>提供 Spring Boot 自动配置和 Bean 装配
 *   <li>实现错误映射 SPI,将领域异常映射为 HTTP 错误码
 *   <li>配置适配器层的横切关注点(日志、监控等)
 *   <li>注册自定义转换器和格式化器
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.registry.config.RegistryErrorMappingContributor} - Registry 异常映射贡献者
 *       <ul>
 *         <li>将 {@code DomainValidationException} 映射为 {@code BAD_REQUEST}
 *         <li>将 {@code RegistryQuotaExceeded} 映射为 {@code CONFLICT}
 *         <li>将 {@code DuplicateKeyException} 映射为 {@code CONFLICT}
 *         <li>将 {@code DataIntegrityViolationException} 映射为 {@code UNPROCESSABLE}
 *         <li>将 {@code OptimisticLockingFailureException} 映射为 {@code CONFLICT}
 *       </ul>
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>SPI 机制</b>: 通过 {@code ErrorMappingContributor} 接口扩展全局异常映射
 *   <li><b>显式映射</b>: 为领域异常提供精确的 HTTP 错误码映射
 *   <li><b>从 boot 迁移</b>: 配置从 boot 模块迁移到 adapter,避免 boot 直接依赖 domain/api
 * </ul>
 *
 * <h2>错误映射规则</h2>
 *
 * <table border="1">
 *   <caption>Registry 异常到 HTTP 错误码映射表</caption>
 *   <tr>
 *     <th>异常类型</th>
 *     <th>HTTP 状态码</th>
 *     <th>错误码前缀</th>
 *     <th>说明</th>
 *   </tr>
 *   <tr>
 *     <td>{@code DomainValidationException}</td>
 *     <td>400</td>
 *     <td>REG-04xx</td>
 *     <td>领域验证失败</td>
 *   </tr>
 *   <tr>
 *     <td>{@code RegistryQuotaExceeded}</td>
 *     <td>409</td>
 *     <td>REG-09xx</td>
 *     <td>配额超限</td>
 *   </tr>
 *   <tr>
 *     <td>{@code DuplicateKeyException}</td>
 *     <td>409</td>
 *     <td>REG-09xx</td>
 *     <td>唯一键冲突</td>
 *   </tr>
 *   <tr>
 *     <td>{@code DataIntegrityViolationException}</td>
 *     <td>422</td>
 *     <td>REG-22xx</td>
 *     <td>数据完整性违反</td>
 *   </tr>
 *   <tr>
 *     <td>{@code OptimisticLockingFailureException}</td>
 *     <td>409</td>
 *     <td>REG-09xx</td>
 *     <td>乐观锁冲突</td>
 *   </tr>
 * </table>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Component
 * public class RegistryErrorMappingContributor implements ErrorMappingContributor {
 *     private final HttpStdErrors.Group http;
 *
 *     @Override
 *     public Optional<ErrorCodeLike> mapException(Throwable exception) {
 *         if (exception instanceof DomainValidationException) {
 *             return Optional.of(http.BAD_REQUEST());
 *         }
 *         // ...其他映射规则
 *         return Optional.empty();
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.config;
