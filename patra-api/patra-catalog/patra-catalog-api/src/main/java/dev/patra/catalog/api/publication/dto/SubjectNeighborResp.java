package dev.patra.catalog.api.publication.dto;

import lombok.Builder;

/// 一篇同主题邻居。
///
/// @param id 文献主键（经 MVC 转换器输出为 JSON 字符串）
/// @param reason 来源
/// @param meshDescriptorUi 命中的主要主题词 UI；同期刊时为 null
/// @param meshTerm 命中的主要主题词原文；同期刊时为 null
/// @param venueName 期刊全称；同主题词时为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record SubjectNeighborResp(
    Long id,
    SubjectNeighborReasonResp reason,
    String meshDescriptorUi,
    String meshTerm,
    String venueName) {}
