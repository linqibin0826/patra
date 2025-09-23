package com.patra.registry.api.rpc.dto.provenance;

import java.util.List;

/**
 * 数据来源（Provenance）完整配置聚合响应 DTO。<br>
 * <p>用途：供外部调用方（例如 Ingest 采集引擎 / 表达式编排层）一次性拉取某一
 * {@link ProvenanceResp} 在特定作用域/任务维度下生效的“端点 + 窗口/指针 + 分页 + HTTP + 批量 + 重试 + 限流 + 凭证”组合配置，
 * 减少多次 RPC 往返并保证快照一致性。</p>
 * <p>对应关系（Record 组件 → 逻辑表 / 配置来源）：
 * <ul>
 *   <li>{@code provenance} → 主数据表 reg_provenance</li>
 *   <li>{@code endpoint} → reg_prov_endpoint_def（端点定义，可选）</li>
 *   <li>{@code windowOffset} → reg_prov_window_offset_cfg（增量 / 时间窗口，可选）</li>
 *   <li>{@code pagination} → reg_prov_pagination_cfg（分页/游标策略，可选）</li>
 *   <li>{@code http} → reg_prov_http_cfg（HTTP 通信基础策略，可选）</li>
 *   <li>{@code batching} → reg_prov_batching_cfg（批量 / 请求成型与并发组织，可选）</li>
 *   <li>{@code retry} → reg_prov_retry_cfg（错误重试与退避策略，可选）</li>
 *   <li>{@code rateLimit} → reg_prov_rate_limit_cfg（速率与并发限制，可选）</li>
 *   <li>{@code credentials} → reg_prov_credential（一个来源/端点下可能存在多条有效凭证）</li>
 * </ul>
 * “可选”表示该来源在当前 scope/taskType/taskTypeKey 与时间片内未配置或未生效时可返回 {@code null} 或空集合；业务方需做好空值兜底。</p>
 * <p>一致性说明：聚合生成时建议在同一事务 / 配置版本快照下查询，确保时间片（effective_from / effective_to）判定统一，否则可能出现跨片段撕裂。</p>
 * <p>使用建议：
 * <ol>
 *   <li>获取后在调用方内做不可变缓存（key=provenance.code+scope+taskType+taskTypeKey），并结合配置版本或更新时间做失效。</li>
 *   <li>当某组件为 {@code null} 时按默认引擎策略（例如：无分页时视为单页；无限流时遵循调用方全局限流）。</li>
 *   <li>凭证数组可能包含多条：需按照 {@code defaultPreferred} / 生命周期状态过滤选择。</li>
 * </ol>
 * </p>
 *
 * @param provenance   基础来源元数据（必填）。驱动所有后续配置的逻辑主键（code）与默认属性（基础 Base URL / 时区 / 激活状态等）。
 * @param endpoint     端点定义。描述任务对应的 HTTP 接入点（方法、路径模板、默认 Query/Body、鉴权需求、分页参数名等）。可能为 null 代表任务不依赖单一固定端点（例如内部生成数据）。
 * @param windowOffset 时间窗口与增量指针策略。控制增量抓取的时间切片/回溯/重叠与偏移字段解析模式。无增量语义任务可为 null。
 * @param pagination   分页/游标抽取策略。定义分页模式（页号 / 游标 / 混合）、参数名、起始页、JSONPath/XPath 提取下一个游标等。非分页接口可为 null。
 * @param http         HTTP 基础策略（Header 默认值、超时、TLS 校验、代理、Idempotency Header 等）。未覆盖时使用引擎全局默认。
 * @param batching     批量抓取与请求成型策略（ID 聚合、并发度、连接池、背压策略、请求模板、压缩策略等）。无批量需求可为 null。
 * @param retry        重试与退避配置（最大重试次数、退避策略、白/黑名单状态码、网络错误是否重试、熔断阈值等）。缺省时按引擎默认策略执行。
 * @param rateLimit    限流与并发表面控制（令牌速率、突发桶容量、最大并发、按凭证粒度限制、服务器头遵守等）。未配置即不做来源专属限制。
 * @param credentials  鉴权凭证列表（可能多条：API Key / Basic / OAuth 等）。调用方需根据 endpoint.authRequired 与各凭证生命周期、默认偏好选择。允许为空列表表示开放或无需鉴权。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigResp(
        /** 基础来源元数据（reg_provenance） */
        ProvenanceResp provenance,
        /** 端点定义（reg_prov_endpoint_def），可能为空 */
        EndpointDefinitionResp endpoint,
        /** 增量窗口/指针配置（reg_prov_window_offset_cfg），可能为空 */
        WindowOffsetResp windowOffset,
        /** 分页 / 游标配置（reg_prov_pagination_cfg），可能为空 */
        PaginationConfigResp pagination,
        /** HTTP 通信策略（reg_prov_http_cfg），可能为空 */
        HttpConfigResp http,
        /** 批量与请求成型策略（reg_prov_batching_cfg），可能为空 */
        BatchingConfigResp batching,
        /** 重试与退避策略（reg_prov_retry_cfg），可能为空 */
        RetryConfigResp retry,
        /** 限流与并发策略（reg_prov_rate_limit_cfg），可能为空 */
        RateLimitConfigResp rateLimit,
        /** 凭证列表（reg_prov_credential），可为空列表 */
        List<CredentialResp> credentials
) {
}
