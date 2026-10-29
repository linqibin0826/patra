package dev.patra.catalog.domain.venue.model.read;

import lombok.Builder;

/// 期刊解析读模型：库里存在的一个期刊（含软删），供 interaction 的对象登记表首次解析与对账。
///
/// @param id 期刊主键
/// @param nlmId 主 NLM（身份锚，按字符串保留前导零；库里为空时为 null）
/// @param title 期刊全称（`cat_venue.title`）
/// @param abbreviatedTitle MEDLINE 刊名缩写（`cat_venue.abbreviated_title`，可空）
/// @param deleted 是否已软删
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueResolveReadModel(
    Long id, String nlmId, String title, String abbreviatedTitle, boolean deleted) {}
