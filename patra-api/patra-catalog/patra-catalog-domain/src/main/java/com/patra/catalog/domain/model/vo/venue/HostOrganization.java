package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/// 宿主机构值对象。封装载体的发布/托管机构信息。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 完整性：包含机构 ID、名称和所有权链
/// - 来源追溯：ID 为 OpenAlex Institution ID
///
/// 数据来源：
///
/// 主要来自 OpenAlex Source 的 `host_organization`、`host_organization_name`
/// 和 `host_organization_lineage` 字段。
///
/// 使用示例：
///
/// ```java
/// // 创建宿主机构（带所有权链）
/// HostOrganization host = HostOrganization.of(
///     "I123456789",
///     "Elsevier",
///     List.of("I123456789", "I987654321")
/// );
///
/// // 创建简单宿主机构（无所有权链）
/// HostOrganization simple = HostOrganization.of("I123456789", "Springer Nature");
/// ```
///
/// @param id 机构 ID（OpenAlex Institution ID，必填）
/// @param name 机构名称（必填）
/// @param lineage 所有权链（从当前机构到顶层母公司的 ID 列表，可选）
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
public record HostOrganization(String id, String name, List<String> lineage)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证宿主机构信息的有效性。
  ///
  /// @throws IllegalArgumentException 如果 ID 或名称为空
  public HostOrganization {
    Assert.notBlank(id, "宿主机构 ID 不能为空");
    Assert.notBlank(name, "宿主机构名称不能为空");

    // 防御性复制，确保不可变性
    lineage = lineage != null ? List.copyOf(lineage) : List.of();
  }

  /// 创建宿主机构（带所有权链）。
  ///
  /// @param id 机构 ID
  /// @param name 机构名称
  /// @param lineage 所有权链
  /// @return 宿主机构值对象
  public static HostOrganization of(String id, String name, List<String> lineage) {
    return new HostOrganization(id, name, lineage);
  }

  /// 创建宿主机构（无所有权链）。
  ///
  /// @param id 机构 ID
  /// @param name 机构名称
  /// @return 宿主机构值对象
  public static HostOrganization of(String id, String name) {
    return new HostOrganization(id, name, List.of());
  }

  /// 判断是否有所有权链。
  ///
  /// @return true 如果有所有权链
  public boolean hasLineage() {
    return lineage != null && !lineage.isEmpty();
  }

  /// 获取顶层母公司 ID。
  ///
  /// @return 顶层母公司 ID，如果无所有权链则返回当前机构 ID
  @JsonIgnore
  public String getTopParentId() {
    return hasLineage() ? lineage.getLast() : id;
  }
}
