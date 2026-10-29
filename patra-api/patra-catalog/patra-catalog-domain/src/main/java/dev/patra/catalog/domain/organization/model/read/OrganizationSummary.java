package dev.patra.catalog.domain.organization.model.read;

import java.util.List;
import lombok.Builder;

/// 机构摘要读模型：建议接口与内部查询共用的那组字段。
///
/// 只暴露 ROR ID 这个自然键，不暴露 catalog 的内部 ID（重导时会变）。
///
/// @param rorId 9 位裸 ROR ID
/// @param displayName ROR 显示名（英文）
/// @param zhName 中文名（label 优先、同级最短），没有为 `null`
/// @param countryCode 第一条地点的国家码，没有为 `null`
/// @param cityName 第一条地点的城市名，没有为 `null`
/// @param types ROR 类型码（如 `healthcare`、`education`），没有为空列表
/// @author linqibin
/// @since 0.1.0
@Builder
public record OrganizationSummary(
    String rorId,
    String displayName,
    String zhName,
    String countryCode,
    String cityName,
    List<String> types) {

  /// 紧凑构造器：`types` 防御性拷贝，`null` 视为空列表。
  public OrganizationSummary {
    types = types != null ? List.copyOf(types) : List.of();
  }
}
