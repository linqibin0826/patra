package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 批量抓取与请求成型（Batching & Shaping）配置响应 DTO。<br>
 * <p>对应表：reg_prov_batching_cfg。控制 ID 聚合、并行度及请求模板化。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code scopeCode} 作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 任务子键。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code detailFetchBatchSize} 明细抓取时单批 ID 数（驱动内部分片）。</li>
 *   <li>{@code endpointId} 优先绑定的端点（可覆盖聚合中默认 endpoint）。</li>
 *   <li>{@code credentialName} 指定使用的凭证（为空则由调度器决策）。</li>
 *   <li>{@code idsParamName} 批量 ID 参数名（例如 ids / id_list）。</li>
 *   <li>{@code idsJoinDelimiter} ID 以字符串拼接的分隔符（如 , 或 +）。</li>
 *   <li>{@code maxIdsPerRequest} 单请求允许的最大 ID 数（服务端限制）。</li>
 *   <li>{@code preferCompactPayload} 是否偏好紧凑（最小冗余）请求体。</li>
 *   <li>{@code payloadCompressStrategyCode} 请求体发送前压缩策略（NONE / GZIP / AUTO）。</li>
 *   <li>{@code appParallelismDegree} 应用层处理并行度（线程/异步分片建议值）。</li>
 *   <li>{@code perHostConcurrencyLimit} 针对同 Host 的最大并发请求数。</li>
 *   <li>{@code httpConnPoolSize} HTTP 连接池建议大小（可与并发度协商）。</li>
 *   <li>{@code backpressureStrategyCode} 背压策略（BLOCK / DROP / QUEUE / YIELD）。</li>
 *   <li>{@code requestTemplateJson} 请求模板（可包含变量占位符用于渲染）。</li>
 * </ul>
 */
public record BatchingConfigResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 作用域编码 */
        String scopeCode,
        /** 任务类型 */
        String taskType,
        /** 任务子键 */
        String taskTypeKey,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 明细批量拉取时单批大小 */
        Integer detailFetchBatchSize,
        /** 绑定端点 ID */
        Long endpointId,
        /** 指定凭证名 */
        String credentialName,
        /** ID 参数名 */
        String idsParamName,
        /** ID 连接分隔符 */
        String idsJoinDelimiter,
        /** 单请求最大 ID 数 */
        Integer maxIdsPerRequest,
        /** 是否偏好紧凑负载 */
        boolean preferCompactPayload,
        /** 负载压缩策略编码 */
        String payloadCompressStrategyCode,
        /** 应用层建议并行度 */
        Integer appParallelismDegree,
        /** 单 Host 并发限制 */
        Integer perHostConcurrencyLimit,
        /** HTTP 连接池大小 */
        Integer httpConnPoolSize,
        /** 背压策略编码 */
        String backpressureStrategyCode,
        /** 请求模板 JSON */
        String requestTemplateJson
) {
}
