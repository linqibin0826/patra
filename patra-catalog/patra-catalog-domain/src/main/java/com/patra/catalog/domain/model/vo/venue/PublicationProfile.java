package com.patra.catalog.domain.model.vo.venue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/// 出版概况值对象。封装期刊的出版相关元数据。
///
/// **设计原则**：
///
/// - 不可变性：Record + 防御性复制
/// - 组合复用：复用已有值对象（PublicationHistory、VenueLanguages、HostOrganization、IndexingInfo）
/// - 语义化命名：聚焦出版相关信息，与 CitationMetrics（引用指标）、OpenAccessInfo（OA 信息）职责分离
///
/// **Jackson 注解设计决策**：
///
/// 本值对象使用了 `@JsonIgnoreProperties` 和 `@JsonIgnore` 注解，这是有意为之的设计权衡：
///
/// - **使用场景**：该值对象通过 JPA JSON 字段存储在数据库中，需要支持 JSON 序列化/反序列化
/// - **设计取舍**：Jackson 是标准 JSON 库而非 Spring 框架依赖，在领域层使用是可接受的技术选择
/// - **替代方案**：若需严格保持领域层纯净，可将序列化配置移至 Infrastructure 层的 ObjectMapper
/// - **@JsonIgnore 用途**：标记派生的便捷方法（如 isCeased()），避免序列化时产生冗余字段
///
/// **包含的数据**：
///
/// | 分类 | 字段 | 来源 |
/// |------|------|------|
/// | 出版信息 | abbreviatedTitle, alternateTitles, homepageUrl, frequency | OpenAlex |
/// | 出版历史 | publicationHistory | OpenAlex/PubMed |
/// | 语言信息 | languages | Serfile |
/// | 宿主机构 | hostOrganization | OpenAlex |
/// | 地理信息 | countryCode | OpenAlex |
/// | 索引信息 | indexingInfo | PubMed Catalog |
/// | 扩展数据 | extData | 各来源 |
///
/// **使用示例**：
///
/// ```java
/// // 从 OpenAlex 数据构建
/// PublicationProfile profile = PublicationProfile.builder()
///     .abbreviatedTitle("Nat. Med.")
///     .alternateTitles(List.of("Nature Medicine"))
///     .homepageUrl("https://www.nature.com/nm")
///     .frequency("Monthly")
///     .publicationHistory(PublicationHistory.active(1995))
///     .hostOrganization(HostOrganization.of("I123", "Springer Nature"))
///     .build();
/// ```
///
/// @param abbreviatedTitle 缩写标题（ISO 缩写）
/// @param alternateTitles 替代名称列表
/// @param homepageUrl 主页 URL
/// @param frequency 出版频率（如 Weekly/Monthly/Quarterly）
/// @param publicationHistory 出版历史（创刊/停刊年份）
/// @param languages 语言信息（主语言和摘要语言）
/// @param hostOrganization 宿主机构信息
/// @param countryCode 国家代码（ISO 3166-1 alpha-2，由 registry 服务标准化）
/// @param indexingInfo MEDLINE 索引收录信息
/// @param extData 扩展数据（JSON 存储的其他字段）
/// @author linqibin
/// @since 0.7.0
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicationProfile(
    String abbreviatedTitle,
    List<String> alternateTitles,
    String homepageUrl,
    String frequency,
    PublicationHistory publicationHistory,
    VenueLanguages languages,
    HostOrganization hostOrganization,
    String countryCode,
    IndexingInfo indexingInfo,
    Map<String, Object> extData)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 规范化构造函数，确保集合类型不可变。
  public PublicationProfile {
    alternateTitles = alternateTitles != null ? List.copyOf(alternateTitles) : List.of();
    extData = extData != null ? Map.copyOf(extData) : Map.of();
  }

  /// 创建空的 PublicationProfile。
  ///
  /// @return 空的出版概况值对象
  public static PublicationProfile empty() {
    return PublicationProfile.builder().build();
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否有缩写标题。
  ///
  /// @return true 如果有缩写标题
  public boolean hasAbbreviatedTitle() {
    return abbreviatedTitle != null && !abbreviatedTitle.isBlank();
  }

  /// 判断是否有替代名称。
  ///
  /// @return true 如果有替代名称
  public boolean hasAlternateTitles() {
    return alternateTitles != null && !alternateTitles.isEmpty();
  }

  /// 判断是否有主页 URL。
  ///
  /// @return true 如果有主页 URL
  public boolean hasHomepageUrl() {
    return homepageUrl != null && !homepageUrl.isBlank();
  }

  /// 判断是否有出版频率。
  ///
  /// @return true 如果有出版频率
  public boolean hasFrequency() {
    return frequency != null && !frequency.isBlank();
  }

  /// 判断是否有出版历史。
  ///
  /// @return true 如果有出版历史
  public boolean hasPublicationHistory() {
    return publicationHistory != null;
  }

  /// 判断是否有语言信息。
  ///
  /// @return true 如果有语言信息
  public boolean hasLanguages() {
    return languages != null && !languages.isEmpty();
  }

  /// 判断是否有宿主机构。
  ///
  /// @return true 如果有宿主机构
  public boolean hasHostOrganization() {
    return hostOrganization != null;
  }

  /// 判断是否有国家代码。
  ///
  /// @return true 如果有国家代码
  public boolean hasCountryCode() {
    return countryCode != null && !countryCode.isBlank();
  }

  /// 判断是否有索引信息。
  ///
  /// @return true 如果有索引信息
  public boolean hasIndexingInfo() {
    return indexingInfo != null;
  }

  /// 判断是否有扩展数据。
  ///
  /// @return true 如果有扩展数据
  public boolean hasExtData() {
    return extData != null && !extData.isEmpty();
  }

  // ========== 组合值对象的便捷代理方法 ==========

  /// 判断期刊是否已停刊。
  ///
  /// @return true 如果已停刊
  @JsonIgnore
  public boolean isCeased() {
    return publicationHistory != null && publicationHistory.ceased();
  }

  /// 判断期刊是否当前被 MEDLINE 收录。
  ///
  /// @return true 如果当前被 MEDLINE 索引
  @JsonIgnore
  public boolean isCurrentlyIndexed() {
    return indexingInfo != null && indexingInfo.isCurrentlyIndexed();
  }

  /// 判断是否为英语期刊。
  ///
  /// @return true 如果主语言为英语
  @JsonIgnore
  public boolean isEnglishJournal() {
    return languages != null && languages.isEnglish();
  }

  /// 判断是否为中文期刊。
  ///
  /// @return true 如果主语言为中文
  @JsonIgnore
  public boolean isChineseJournal() {
    return languages != null && languages.isChinese();
  }

  /// 获取创刊年份。
  ///
  /// @return 创刊年份，如果无出版历史则返回 null
  @JsonIgnore
  public Integer getStartYear() {
    return publicationHistory != null ? publicationHistory.startYear() : null;
  }

  /// 获取停刊年份。
  ///
  /// @return 停刊年份，如果未停刊或无出版历史则返回 null
  @JsonIgnore
  public Integer getEndYear() {
    return publicationHistory != null ? publicationHistory.endYear() : null;
  }

  /// 获取 MEDLINE 缩写标题。
  ///
  /// @return MEDLINE 缩写标题，如果无索引信息则返回 null
  @JsonIgnore
  public String getMedlineTa() {
    return indexingInfo != null ? indexingInfo.medlineTa() : null;
  }

  /// 获取 ISO 缩写标题。
  ///
  /// @return ISO 缩写标题，如果无索引信息则返回 null
  @JsonIgnore
  public String getIsoAbbreviation() {
    return indexingInfo != null ? indexingInfo.isoAbbreviation() : null;
  }

  /// 获取主要语言。
  ///
  /// @return 主要语言代码，如果无语言信息则返回 null
  @JsonIgnore
  public String getMainLanguage() {
    return languages != null ? languages.getMainLanguage() : null;
  }

  /// 获取宿主机构名称。
  ///
  /// @return 宿主机构名称，如果无宿主机构则返回 null
  @JsonIgnore
  public String getHostOrganizationName() {
    return hostOrganization != null ? hostOrganization.name() : null;
  }

  /// 获取宿主机构 ID。
  ///
  /// @return 宿主机构 ID，如果无宿主机构则返回 null
  @JsonIgnore
  public String getHostOrganizationId() {
    return hostOrganization != null ? hostOrganization.id() : null;
  }
}
