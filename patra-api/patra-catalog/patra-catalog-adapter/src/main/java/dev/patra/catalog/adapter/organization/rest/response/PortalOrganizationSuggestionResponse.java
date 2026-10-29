package dev.patra.catalog.adapter.organization.rest.response;

import java.util.List;
import lombok.Builder;

/// 机构建议的一条候选。
///
/// @param rorId 9 位裸 ROR ID
/// @param displayName ROR 显示名（英文）
/// @param zhName 中文名，没有为 `null`
/// @param matchedName 命中查询的名称原文
/// @param countryCode 国家码，没有为 `null`
/// @param cityName 城市名，没有为 `null`
/// @param types ROR 类型码
/// @author linqibin
/// @since 0.1.0
@Builder
public record PortalOrganizationSuggestionResponse(
    String rorId,
    String displayName,
    String zhName,
    String matchedName,
    String countryCode,
    String cityName,
    List<String> types) {

  /// 紧凑构造器：`types` 防御性拷贝，`null` 视为空列表。
  public PortalOrganizationSuggestionResponse {
    types = types != null ? List.copyOf(types) : List.of();
  }
}
