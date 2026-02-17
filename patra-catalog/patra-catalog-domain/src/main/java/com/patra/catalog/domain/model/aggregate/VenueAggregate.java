package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;

/// 出版载体聚合根。
///
/// **CQRS 设计**：
///
/// 本聚合根遵循 CQRS 模式，**仅用于写入侧**（数据采集、更新）：
///
/// - 只包含验证不变量所需的最小属性集
/// - 数据从 PubMed/NLM Catalog 流入，经聚合根验证后持久化
/// - 读取场景通过 DO 或专门的读模型实现，不经过聚合根重建
///
/// **聚合边界**：
///
/// - VenueIdentifier（值对象，1:N）：标识符集合，保护 ISSN-L 唯一性不变量
///
/// **嵌入式值对象**（作为 JSON 字段存储在主表）：
///
/// - PublicationProfile：出版概况（出版信息、索引状态、宿主机构等）
/// - CitationMetrics：引用指标（works_count、cited_by_count 等）
/// - OpenAccessInfo：开放获取信息（OA 状态 + APC 定价）
/// - Society：关联学会列表
///
/// **关联数据**（通过 VenueRepository 独立管理，不属于聚合边界）：
///
/// - VenuePublicationStats：年度指标集合
/// - VenueMesh：MeSH 主题词集合（来自 Serfile）
/// - VenueRelation：期刊关联关系集合（来自 Serfile）
/// - VenueIndexingHistory：索引历史记录集合（来自 Serfile）
///
/// **验证规则**：
///
/// - 所有类型：title 和 venueType 必填
/// - 来自 PubMed：必须包含 NLM 或 ISSN_L 类型的标识符
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueAggregate extends AggregateRoot<VenueId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 核心属性（最小集） ==========

  /// 载体类型（必填，不变量）
  private final VenueType venueType;

  /// 标题（必填，不变量）
  private final String title;

  /// 中文标题（可空，来自 Wikidata SPARQL 查询，可后续富化）
  private String titleZh;

  /// 标识符集合（聚合边界内值对象）
  private final List<VenueIdentifier> identifiers;

  /// 数据来源信息
  private ProvenanceInfo provenance;

  // ========== 嵌入式值对象 ==========

  /// 出版概况（出版信息、索引状态、宿主机构等）
  private PublicationProfile publicationProfile;

  /// 引用指标（works_count、cited_by_count 等）
  private CitationMetrics citationMetrics;

  /// 开放获取信息（OA 状态 + APC 定价）
  private OpenAccessInfo openAccess;

  /// 关联学会列表
  private List<Society> affiliatedSocieties;

  /// 私有构造函数（通过工厂方法创建）。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueType 载体类型
  /// @param title 标题
  /// @param titleZh 中文标题（可空）
  private VenueAggregate(VenueId id, VenueType venueType, String title, String titleZh) {
    super(id);

    Assert.notNull(venueType, "载体类型不能为空");
    Assert.notBlank(title, "标题不能为空");

    this.venueType = venueType;
    this.title = title;
    this.titleZh = titleZh;
    this.identifiers = new ArrayList<>();
    this.affiliatedSocieties = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 从 PubMed 创建期刊载体。
  ///
  /// @param title 期刊名称
  /// @param titleZh 中文标题（可空，来自 Wikidata）
  /// @param nlmId NLM 唯一标识符（nlmId 或 issnL 至少一个必填）
  /// @param issnL Linking ISSN（nlmId 或 issnL 至少一个必填）
  /// @return 载体聚合根
  public static VenueAggregate fromPubMed(
      String title, String titleZh, String nlmId, String issnL) {
    Assert.isTrue(
        StrUtil.isNotBlank(nlmId) || StrUtil.isNotBlank(issnL),
        "来自 PubMed 的载体必须提供 NLM ID 或 ISSN-L");

    VenueAggregate aggregate = new VenueAggregate(null, VenueType.JOURNAL, title, titleZh);

    // 添加标识符
    if (StrUtil.isNotBlank(nlmId)) {
      aggregate.addIdentifier(VenueIdentifier.forNlm(nlmId));
    }
    if (StrUtil.isNotBlank(issnL)) {
      aggregate.addIdentifier(VenueIdentifier.forIssnL(issnL));
    }

    // 设置来源信息
    aggregate.provenance = ProvenanceInfo.forPubMed();

    return aggregate;
  }

  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID（VenueId 值对象）
  /// @param venueType 载体类型
  /// @param title 标题
  /// @param titleZh 中文标题（可空）
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static VenueAggregate restore(
      VenueId id, VenueType venueType, String title, String titleZh, Long version) {
    VenueAggregate aggregate = new VenueAggregate(id, venueType, title, titleZh);
    aggregate.assignVersion(version != null ? version : 0L);
    return aggregate;
  }

  // ========== 属性设置方法 ==========

  /// 设置数据来源信息。
  ///
  /// @param provenance 来源信息
  /// @return 当前对象
  public VenueAggregate withProvenance(ProvenanceInfo provenance) {
    this.provenance = provenance;
    return this;
  }

  // ========== 富化方法 ==========

  /// 富化中文标题。
  ///
  /// 用于 PubMed 导入时为已存在的 Venue 补充 Wikidata 查询到的中文标题。
  /// 传入 null 时不做任何操作（表示未查询到数据，不应清除已有值）。
  ///
  /// @param titleZh 中文标题（null 表示无数据，不清除已有值）
  public void enrichTitleZh(String titleZh) {
    if (titleZh != null) {
      this.titleZh = titleZh;
    }
  }

  // ========== 嵌入式值对象设置方法 ==========

  /// 设置出版概况。
  ///
  /// @param publicationProfile 出版概况
  /// @return 当前对象
  public VenueAggregate withPublicationProfile(PublicationProfile publicationProfile) {
    this.publicationProfile = publicationProfile;
    return this;
  }

  /// 标准化国家编码。
  ///
  /// 用于数据导入时验证和更新国家编码。调用者先通过 registry 服务验证原始编码，
  /// 然后将验证结果传入此方法。如果验证结果与当前编码不同，则更新。
  ///
  /// **设计原则**：
  ///
  /// - 聚合根内部封装状态变更逻辑
  /// - Infrastructure 层只负责获取验证结果，不直接修改聚合状态
  ///
  /// @param validatedCode 经过 registry 服务验证后的国家编码，null 表示原编码无效
  public void normalizeCountryCode(String validatedCode) {
    if (publicationProfile == null) {
      return;
    }

    String currentCode = publicationProfile.countryCode();
    if (Objects.equals(currentCode, validatedCode)) {
      return; // 无需更新
    }

    // 使用 toBuilder 保留其他字段，只更新 countryCode
    this.publicationProfile = publicationProfile.toBuilder().countryCode(validatedCode).build();
  }

  /// 标准化语言代码。
  ///
  /// 用于数据导入时验证和转换语言代码。调用者先通过 registry 服务批量验证原始语言代码，
  /// 然后将验证映射结果（原始代码 → BCP 47 标准代码）传入此方法。
  ///
  /// **处理逻辑**：
  ///
  /// - 遍历 primary 和 summary 列表中的每个语言代码
  /// - 使用映射表将有效代码转换为 BCP 47 格式
  /// - 无效代码（不在映射表中）被过滤掉
  /// - 对结果列表去重（因为多个 ISO 639-3 代码可能映射到同一个 BCP 47 代码）
  /// - 保持原始顺序（使用 LinkedHashSet）
  ///
  /// **设计原则**：
  ///
  /// - 聚合根内部封装状态变更逻辑
  /// - Infrastructure 层只负责获取验证结果，不直接修改聚合状态
  ///
  /// @param validatedMappings 经过 registry 服务验证后的映射（原始代码 → BCP 47 代码）
  public void normalizeLanguages(Map<String, String> validatedMappings) {
    if (publicationProfile == null) {
      return;
    }

    VenueLanguages currentLanguages = publicationProfile.languages();
    if (currentLanguages == null || currentLanguages.isEmpty()) {
      return;
    }

    // 转换并去重 primary 列表
    List<String> normalizedPrimary =
        currentLanguages.primary().stream()
            .map(validatedMappings::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();

    // 转换并去重 summary 列表
    List<String> normalizedSummary =
        currentLanguages.summary().stream()
            .map(validatedMappings::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new))
            .stream()
            .toList();

    VenueLanguages normalizedLanguages = VenueLanguages.of(normalizedPrimary, normalizedSummary);

    // 只有当语言发生变化时才更新
    if (Objects.equals(currentLanguages, normalizedLanguages)) {
      return;
    }

    this.publicationProfile = publicationProfile.toBuilder().languages(normalizedLanguages).build();
  }

  /// 设置引用指标。
  ///
  /// @param citationMetrics 引用指标
  /// @return 当前对象
  public VenueAggregate withCitationMetrics(CitationMetrics citationMetrics) {
    this.citationMetrics = citationMetrics;
    return this;
  }

  /// 设置开放获取信息。
  ///
  /// @param openAccess 开放获取信息
  /// @return 当前对象
  public VenueAggregate withOpenAccess(OpenAccessInfo openAccess) {
    this.openAccess = openAccess;
    return this;
  }

  /// 设置关联学会列表。
  ///
  /// @param affiliatedSocieties 关联学会列表
  /// @return 当前对象
  public VenueAggregate withAffiliatedSocieties(List<Society> affiliatedSocieties) {
    this.affiliatedSocieties =
        affiliatedSocieties != null ? new ArrayList<>(affiliatedSocieties) : new ArrayList<>();
    return this;
  }

  /// 获取关联学会列表（不可变视图）。
  ///
  /// @return 关联学会列表
  public List<Society> getAffiliatedSocieties() {
    return Collections.unmodifiableList(affiliatedSocieties);
  }

  // ========== 标识符管理方法 ==========

  /// 添加标识符。
  ///
  /// 如果已存在相同类型和值的标识符，则忽略。
  /// 成功添加后会将聚合根标记为脏，触发版本号递增。
  ///
  /// @param identifier 标识符
  public void addIdentifier(VenueIdentifier identifier) {
    Assert.notNull(identifier, "标识符不能为空");

    // 检查是否已存在相同的标识符（基于 Record 的 equals）
    if (!identifiers.contains(identifier)) {
      identifiers.add(identifier);
    }
  }

  /// 添加标识符（便捷方法）。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  public void addIdentifier(VenueIdentifierType type, String value) {
    addIdentifier(new VenueIdentifier(type, value));
  }

  /// 移除标识符。
  ///
  /// 成功移除后会将聚合根标记为脏，触发版本号递增。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @return 是否成功移除
  public boolean removeIdentifier(VenueIdentifierType type, String value) {
    VenueIdentifier target = new VenueIdentifier(type, value);
    return identifiers.remove(target);
  }

  /// 获取特定类型的标识符值（返回第一个匹配）。
  ///
  /// @param type 标识符类型
  /// @return 标识符值，如果不存在则返回 empty
  public Optional<String> getIdentifier(VenueIdentifierType type) {
    return identifiers.stream()
        .filter(i -> i.type() == type)
        .map(VenueIdentifier::value)
        .findFirst();
  }

  /// 获取特定类型的所有标识符值。
  ///
  /// @param type 标识符类型
  /// @return 标识符值列表
  public List<String> getIdentifiers(VenueIdentifierType type) {
    return identifiers.stream().filter(i -> i.type() == type).map(VenueIdentifier::value).toList();
  }

  /// 获取所有标识符（不可变视图）。
  ///
  /// @return 标识符列表
  public List<VenueIdentifier> getIdentifiers() {
    return Collections.unmodifiableList(identifiers);
  }

  // ========== 便捷判断方法 ==========

  /// 判断是否为期刊。
  ///
  /// @return true 如果为期刊类型
  public boolean isJournal() {
    return venueType.isJournal();
  }

  /// 判断是否为仓库（预印本服务器等）。
  ///
  /// @return true 如果为仓库类型
  public boolean isRepository() {
    return venueType.isRepository();
  }

  /// 判断是否为会议。
  ///
  /// @return true 如果为会议类型
  public boolean isConference() {
    return venueType.isConference();
  }

  /// 判断是否来自 PubMed。
  ///
  /// @return true 如果来自 PubMed
  public boolean isFromPubMed() {
    return provenance != null && provenance.isFromPubMed();
  }

  // ========== 嵌入式值对象代理方法 ==========

  /// 判断是否为开放获取期刊。
  ///
  /// @return true 如果是 OA 期刊
  public boolean isOa() {
    return openAccess != null && openAccess.isOa();
  }

  /// 获取作品总数。
  ///
  /// @return 作品总数，如果无引用指标则返回 null
  public Integer getWorksCount() {
    return citationMetrics != null ? citationMetrics.worksCount() : null;
  }

  /// 获取被引用总次数。
  ///
  /// @return 被引用总次数，如果无引用指标则返回 null
  public Integer getCitedByCount() {
    return citationMetrics != null ? citationMetrics.citedByCount() : null;
  }

  /// 获取 APC 美元价格。
  ///
  /// @return APC 美元价格，如果无开放获取信息则返回 null
  public Integer getApcUsd() {
    return openAccess != null ? openAccess.apcUsd() : null;
  }

  /// 判断期刊是否已停刊。
  ///
  /// @return true 如果已停刊
  public boolean isCeased() {
    return publicationProfile != null && publicationProfile.isCeased();
  }

  /// 判断期刊是否当前被 MEDLINE 收录。
  ///
  /// @return true 如果当前被 MEDLINE 索引
  public boolean isCurrentlyIndexed() {
    return publicationProfile != null && publicationProfile.isCurrentlyIndexed();
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // 载体类型不能为空
    if (venueType == null) {
      throw new IllegalStateException("载体类型不能为空");
    }

    // 标题不能为空
    if (StrUtil.isBlank(title)) {
      throw new IllegalStateException("标题不能为空");
    }
  }

  @Override
  public String toString() {
    String nlmId = getIdentifier(VenueIdentifierType.NLM).orElse(null);
    String issnL = getIdentifier(VenueIdentifierType.ISSN_L).orElse(null);
    return String.format(
        "VenueAggregate[id=%s, type=%s, name=%s, nlmId=%s, issnL=%s]",
        getId(), venueType.getCode(), title, nlmId, issnL);
  }
}
