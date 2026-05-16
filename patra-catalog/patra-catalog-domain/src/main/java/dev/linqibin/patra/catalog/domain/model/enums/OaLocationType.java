package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 开放获取位置类型枚举。
///
/// 字段映射：cat_publication_oa_location.location_type
///
/// OA 内容托管位置类型说明：
///
/// - **PUBLISHER** - 出版商网站（如 Nature、Elsevier 官网）
/// - **REPOSITORY** - 机构/主题仓储（如大学图书馆、Zenodo）
/// - **PUBMED_CENTRAL** - PubMed Central（NIH 的免费全文数据库）
/// - **PREPRINT** - 预印本服务器（如 arXiv、bioRxiv、medRxiv）
/// - **ACADEMIC_SOCIAL** - 学术社交网络（如 ResearchGate、Academia.edu）
/// - **OTHER** - 其他类型
///
/// 优先级排序（选择最佳位置时）：PUBLISHER > PUBMED_CENTRAL > REPOSITORY > PREPRINT > ACADEMIC_SOCIAL > OTHER
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum OaLocationType {

  /// 出版商网站
  PUBLISHER("publisher", "Publisher Website", 100),

  /// PubMed Central
  PUBMED_CENTRAL("pubmed_central", "PubMed Central", 90),

  /// 机构/主题仓储
  REPOSITORY("repository", "Institutional/Subject Repository", 70),

  /// 预印本服务器
  PREPRINT("preprint", "Preprint Server", 60),

  /// 学术社交网络
  ACADEMIC_SOCIAL("academic_social", "Academic Social Network", 40),

  /// 其他类型
  OTHER("other", "Other Location", 10);

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  /// 优先级分数（数值越大优先级越高）
  private final int priority;

  OaLocationType(String code, String description, int priority) {
    this.code = code;
    this.description = description;
    this.priority = priority;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "publisher", "PUBMED_CENTRAL"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static OaLocationType fromCode(String value) {
    Assert.notBlank(value, "OA 位置类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (OaLocationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 OA 位置类型：" + value);
  }

  /// 判断是否为官方来源（出版商或 PMC）。
  ///
  /// @return true 如果为 PUBLISHER 或 PUBMED_CENTRAL
  public boolean isOfficialSource() {
    return this == PUBLISHER || this == PUBMED_CENTRAL;
  }

  /// 判断优先级是否高于指定类型。
  ///
  /// @param other 比较的位置类型
  /// @return true 如果当前类型优先级更高
  public boolean isBetterThan(OaLocationType other) {
    return this.priority > other.priority;
  }
}
