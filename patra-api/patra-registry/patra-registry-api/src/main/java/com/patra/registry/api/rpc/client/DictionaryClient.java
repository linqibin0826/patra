package com.patra.registry.api.rpc.client;

import com.patra.registry.api.rpc.endpoint.DictionaryEndpoint;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * 字典服务的 Feign 客户端。
 *
 * <p>通过服务发现能力供各业务子系统注入，统一访问字典相关能力。
 * 接口继承自 {@link com.patra.registry.api.rpc.endpoint.DictionaryEndpoint}，
 * 保证各微服务之间的 API 一致性。</p>
 *
 * <p>主要用途：</p>
 * - 按类型与编码查询字典项（用于校验与展示）
 * - 获取启用状态的字典项（用于下拉框等）
 * - 获取默认字典项（用于回退值）
 * - 批量校验字典引用
 * - 将外部系统别名解析为内部编码
 * - 监控字典系统健康状况
 *
 * <p>配置说明：</p>
 * - 服务名：{@code patra-registry}（与服务发现注册名一致）
 * - 上下文 ID：{@code dictionaryClient}（该 Feign 客户端 Bean 的唯一标识）
 * - 负载均衡与熔断能力由 Spring Cloud 提供
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Component
 * public class MySubsystemService {
 *     private final DictionaryClient dictionaryClient;
 *     
 *     public void validateEndpointConfig(EndpointConfig config) {
 *         List<DictionaryReference> refs = List.of(
 *             new DictionaryReference("http_method", config.getMethodCode()),
 *             new DictionaryReference("endpoint_type", config.getTypeCode())
 *         );
 *         List<DictionaryValidationQuery> results = dictionaryClient.validateReferences(refs);
 *         // 处理返回的校验结果...
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.registry.api.rpc.endpoint.DictionaryEndpoint
 */
@FeignClient(
    name = "patra-registry",
    contextId = "dictionaryClient"
)
public interface DictionaryClient extends DictionaryEndpoint {
    // 仅继承并复用 HTTP API 契约，不新增方法
}
