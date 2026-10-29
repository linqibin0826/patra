package dev.patra.catalog.api.organization.dto;

import java.util.List;
import lombok.Builder;

/// 机构摘要响应 DTO，暴露给下游服务（identity）。
///
/// 只有 ROR ID 这个自然键，没有 catalog 的内部 ID。名称快照规则：`zhName` 为空才用 `displayName`。
///
/// @param rorId 9 位裸 ROR ID
/// @param displayName ROR 显示名（英文）
/// @param zhName 中文名（Patra 别名表的 LABEL 优先，其次 ROR 的 zh label / alias），没有为 `null`
/// @param countryCode 第一条地点的国家码，没有为 `null`
/// @param types ROR 类型码（如 `healthcare`、`education`）
/// @author linqibin
/// @since 0.1.0
@Builder
public record OrganizationResp(
    String rorId, String displayName, String zhName, String countryCode, List<String> types) {

  /// 紧凑构造器：`types` 防御性拷贝，`null` 视为空列表。
  public OrganizationResp {
    types = types != null ? List.copyOf(types) : List.of();
  }
}
