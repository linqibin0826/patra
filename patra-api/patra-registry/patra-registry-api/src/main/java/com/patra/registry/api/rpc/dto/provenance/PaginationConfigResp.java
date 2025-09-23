package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 分页 / 游标配置响应 DTO。<br>
 * <p>对应表：reg_prov_pagination_cfg。定义调用分页 API 时如何迭代下一页或游标。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code scopeCode} 作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 子任务键。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code paginationModeCode} 模式：PAGE_NUMBER / CURSOR / HYBRID / NONE。</li>
 *   <li>{@code pageSizeValue} 默认请求的页大小（可被端点或 runtime 覆盖）。</li>
 *   <li>{@code maxPagesPerExecution} 单次执行最多翻页数（防止无限迭代）。</li>
 *   <li>{@code pageNumberParamName} 页号参数名（页号模式）。</li>
 *   <li>{@code pageSizeParamName} 页大小参数名。</li>
 *   <li>{@code startPageNumber} 起始页号（通常 1 或 0）。</li>
 *   <li>{@code sortFieldParamName} 排序字段参数名。</li>
 *   <li>{@code sortDirection} 排序方向（ASC/DESC/空）。</li>
 *   <li>{@code cursorParamName} 游标参数名（游标模式）。</li>
 *   <li>{@code initialCursorValue} 首次调用使用的初始游标值（为空表示不传或由服务端生成）。</li>
 *   <li>{@code nextCursorJsonpath} 从 JSON 响应提取下一个游标值的 JSONPath 表达式。</li>
 *   <li>{@code hasMoreJsonpath} JSONPath：布尔型是否有更多。</li>
 *   <li>{@code totalCountJsonpath} JSONPath：总记录数。</li>
 *   <li>{@code nextCursorXpath} XML 响应提取下一游标的 XPath。</li>
 *   <li>{@code hasMoreXpath} XPath：是否有更多。</li>
 *   <li>{@code totalCountXpath} XPath：总数。</li>
 * </ul>
 * JSONPath / XPath 可二选一；均为空时调用方需回退到“基于记录条数”判断或停止策略。
 */
public record PaginationConfigResp(
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
        /** 分页模式编码 */
        String paginationModeCode,
        /** 默认页大小 */
        Integer pageSizeValue,
        /** 单次执行最大页数 */
        Integer maxPagesPerExecution,
        /** 页号参数名 */
        String pageNumberParamName,
        /** 页大小参数名 */
        String pageSizeParamName,
        /** 起始页号 */
        Integer startPageNumber,
        /** 排序字段参数名 */
        String sortFieldParamName,
        /** 排序方向（ASC/DESC） */
        String sortDirection,
        /** 游标参数名 */
        String cursorParamName,
        /** 初始游标值 */
        String initialCursorValue,
        /** 下一个游标 JSONPath */
        String nextCursorJsonpath,
        /** 是否有更多 JSONPath */
        String hasMoreJsonpath,
        /** 总数 JSONPath */
        String totalCountJsonpath,
        /** 下一个游标 XPath */
        String nextCursorXpath,
        /** 是否有更多 XPath */
        String hasMoreXpath,
        /** 总数 XPath */
        String totalCountXpath
) {
}
