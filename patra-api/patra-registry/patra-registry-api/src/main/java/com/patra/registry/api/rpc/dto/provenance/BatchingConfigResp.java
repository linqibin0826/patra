package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 批量抓取与请求成型（Batching & Shaping）配置响应 DTO。<br>
 * <p>对应表：reg_prov_batching_cfg。控制 ID 聚合、并行度及请求模板化。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code operationType} 操作类型（ALL/HARVEST/...）。</li>
 *   <li>{@code operationTypeKey} 操作子键。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code detailFetchBatchSize} 明细抓取时单批 ID 数。</li>
 *   <li>{@code idsParamName} 批量 ID 参数名（例如 ids / id_list）。</li>
 *   <li>{@code idsJoinDelimiter} ID 以字符串拼接的分隔符（如 , 或 +）。</li>
 *   <li>{@code maxIdsPerRequest} 单请求允许的最大 ID 数（服务端限制）。</li>
 * </ul>
 */
public record BatchingConfigResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 操作类型 */
        String operationType,
        /** 操作子键 */
        String operationTypeKey,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 明细批量拉取时单批大小 */
        Integer detailFetchBatchSize,
        /** ID 参数名 */
        String idsParamName,
        /** ID 连接分隔符 */
        String idsJoinDelimiter,
        /** 单请求最大 ID 数 */
        Integer maxIdsPerRequest
) {
}
