/**
 * patra-registry 服务集成适配器。
 *
 * <p>此包包含与 patra-registry 微服务集成的适配器,该服务作为溯源配置、字典和元数据的单一事实来源(SSOT)。
 *
 * <p>关键组件:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.infra.integration.registry.PatraRegistryAdapter} - 实现 {@link
 *       com.patra.ingest.domain.port.PatraRegistryPort} 用于获取溯源配置
 *   <li>{@code converter/} - 防腐层(ACL),用于将注册中心 DTO 转换为领域快照
 * </ul>
 *
 * @since 0.1.0
 */
package com.patra.ingest.infra.integration.registry;
