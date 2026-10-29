package dev.patra.catalog.adapter.portal.rest.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/// Portal 文献流列表查询请求。
///
/// 适配器入口校验：非法参数在此收敛为 4xx，不下沉到应用层。各参数均可空（走服务端默认）。
///
/// @param tab 排序维度（recent/cited，大小写不敏感，可空 → 默认 recent）
/// @param page 页码（≥1，可空 → 默认 1）
/// @param pageSize 每页大小（1..50，可空 → 默认 14）
/// @author linqibin
/// @since 0.1.0
public record PortalPublicationListRequest(
    @Pattern(regexp = "(?i)recent|cited", message = "tab 仅支持 recent 或 cited") String tab,
    @Min(value = 1, message = "page 最小为 1") Integer page,
    @Min(value = 1, message = "pageSize 最小为 1") @Max(value = 50, message = "pageSize 最大为 50")
        Integer pageSize) {}
