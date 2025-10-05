package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 分页 / 游标配置响应 DTO。<br>
 * <p>对应表：reg_prov_pagination_cfg。定义调用分页 API 时如何迭代下一页或游标。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code operationType} 操作类型。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code paginationModeCode} 模式：PAGE_NUMBER / CURSOR / TOKEN / SCROLL。</li>
 *   <li>{@code pageSizeValue} 默认请求的页大小（可被端点或 runtime 覆盖）。</li>
 *   <li>{@code maxPagesPerExecution} 单次执行最多翻页数（防止无限迭代）。</li>
 *   <li>{@code sortFieldParamName} 排序字段参数名。</li>
 *   <li>{@code sortingDirection} 排序方向：1=ASC，0=DESC。</li>
 * </ul>
 * JSONPath / XPath 可二选一；均为空时调用方需回退到“基于记录条数”判断或停止策略。
 */
public record PaginationConfigResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 操作类型 */
        String operationType,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 分页模式编码 */
        String paginationModeCode,
        /** 默认页大小 */
        Integer pageSizeValue,
        /** 单次执行最大页数 */
        Integer maxPagesPerExecution,
        /** 排序字段参数名 */
        String sortFieldParamName,
        /** 排序方向（1=ASC,0=DESC） */
        Integer sortingDirection
) {
}
