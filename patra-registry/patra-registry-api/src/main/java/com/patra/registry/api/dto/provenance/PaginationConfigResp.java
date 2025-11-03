package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * 分页和游标遍历配置。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>id - 分页配置行的主标识符
 *   <li>provenanceId - 拥有该配置的数据源
 *   <li>operationType - 操作类型鉴别器(如 HARVEST/UPDATE)
 *   <li>effectiveFrom - 配置生效的时间戳
 *   <li>effectiveTo - 配置保持有效的截止时间戳
 *   <li>paginationModeCode - 分页模式(PAGE_NUMBER/CURSOR/TOKEN/SCROLL)
 *   <li>pageSizeValue - 每次调用请求的默认页面大小
 *   <li>maxPagesPerExecution - 每次执行获取的页面数量安全上限
 *   <li>sortFieldParamName - 携带排序字段的请求参数名称
 *   <li>sortingDirection - 排序方向指示符(1=ASC, 0=DESC)
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String paginationModeCode,
    Integer pageSizeValue,
    Integer maxPagesPerExecution,
    String sortFieldParamName,
    Integer sortingDirection) {}
