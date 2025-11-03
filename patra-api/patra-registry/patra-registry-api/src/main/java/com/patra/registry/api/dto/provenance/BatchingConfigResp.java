package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * 数据源 API 请求的批处理配置。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - 批处理配置行的主标识符
 *   <li>provenanceId - 拥有该配置的数据源
 *   <li>operationType - 配置应用的操作类型鉴别器
 *   <li>effectiveFrom - 配置生效的时间戳
 *   <li>effectiveTo - 配置保持有效的截止时间戳
 *   <li>detailFetchBatchSize - 获取详情 ID 时使用的默认批次大小
 *   <li>idsParamName - 携带批处理 ID 的请求参数名称
 *   <li>idsJoinDelimiter - 将 ID 连接成单个值时使用的分隔符
 *   <li>maxIdsPerRequest - 每个出站请求允许的最大 ID 数量
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer detailFetchBatchSize,
    String idsParamName,
    String idsJoinDelimiter,
    Integer maxIdsPerRequest) {}
