package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 端点定义（Endpoint Definition）响应 DTO。<br>
 * <p>对应表：reg_prov_endpoint_def。描述特定来源 + 作用域 + 任务语义下的一个 HTTP 接入点。
 * 提供请求成型的基础结构（方法 / 路径模板 / 默认参数 / 鉴权约束 / 分页参数名等）。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 所属来源 ID（逻辑外键 → reg_provenance.id）。</li>
 *   <li>{@code scopeCode} 配置作用域（区分环境/业务子域）。</li>
 *   <li>{@code taskType} 任务类型（例如 SEARCH / DETAIL / UPDATE）。</li>
 *   <li>{@code taskTypeKey} 任务类型子键（细化区分：如 "article" / "journal"）。</li>
 *   <li>{@code endpointName} 端点内部命名（便于日志/监控聚合）。</li>
 *   <li>{@code endpointUsageCode} 端点用途枚举（如 PRIMARY / CALLBACK / TOKEN）。来自字典 sys_dict_item。</li>
 *   <li>{@code httpMethodCode} HTTP 方法（GET/POST/...）。来自字典 http_method。</li>
 *   <li>{@code pathTemplate} 路径模板（支持占位符：/v1/articles/{id}）。</li>
 *   <li>{@code defaultQueryParamsJson} 默认 Query 参数 JSON（静态键值/数组结构，合并时调用方可后写覆盖）。</li>
 *   <li>{@code defaultBodyPayloadJson} 默认请求体 JSON（POST/PUT 等基线结构）。</li>
 *   <li>{@code requestContentType} 请求内容类型（application/json / application/x-www-form-urlencoded 等）。</li>
 *   <li>{@code authRequired} 是否必须鉴权；为 true 时需选择有效 Credential。</li>
 *   <li>{@code credentialHintName} 推荐优先使用的凭证名（提升多凭证选择效率）。</li>
 *   <li>{@code pageParamName} 分页：页号参数名（当 paginationMode=PAGE_NUMBER 时使用）。</li>
 *   <li>{@code pageSizeParamName} 分页：页大小参数名。</li>
 *   <li>{@code cursorParamName} 分页：游标参数名（paginationMode=CURSOR 时）。</li>
 *   <li>{@code idsParamName} 批量：ID 列表请求参数名（detail 聚合批量获取）。</li>
 *   <li>{@code effectiveFrom} 生效起（含）。</li>
 *   <li>{@code effectiveTo} 生效止（不含，null 表示当前持续有效）。</li>
 * </ul>
 * 生效判定需结合窗口/分页等其它配置的时间片，取同一时间点落入区间的唯一记录或按优先级决策。
 */
public record EndpointDefinitionResp(
        /** 主键 ID */
        Long id,
        /** 所属来源 ID */
        Long provenanceId,
        /** 作用域编码 */
        String scopeCode,
        /** 任务类型 */
        String taskType,
        /** 任务类型子键 */
        String taskTypeKey,
        /** 端点内部名称 */
        String endpointName,
        /** 端点用途枚举编码 */
        String endpointUsageCode,
        /** HTTP 方法编码 */
        String httpMethodCode,
        /** 路径模板（可含占位符） */
        String pathTemplate,
        /** 默认 Query 参数 JSON */
        String defaultQueryParamsJson,
        /** 默认 Body Payload JSON */
        String defaultBodyPayloadJson,
        /** 请求内容类型 */
        String requestContentType,
        /** 是否需要鉴权 */
        boolean authRequired,
        /** 推荐凭证名（选择提示） */
        String credentialHintName,
        /** 页号参数名 */
        String pageParamName,
        /** 页大小参数名 */
        String pageSizeParamName,
        /** 游标参数名 */
        String cursorParamName,
        /** 批量 ID 参数名 */
        String idsParamName,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo
) {
}
