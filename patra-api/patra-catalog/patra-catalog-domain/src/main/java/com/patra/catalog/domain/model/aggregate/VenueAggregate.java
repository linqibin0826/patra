package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.IndexingInfo;
import com.patra.catalog.domain.model.vo.venue.LatestRating;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import com.patra.catalog.domain.model.vo.venue.VenueStats;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/// 出版载体聚合根。管理期刊、仓库、会议等出版载体的完整信息。
///
/// **聚合边界**：
///
/// - VenueIdentifier（值对象，1:N）：标识符集合，保护 ISSN-L 唯一性不变量
///
/// **补充数据（通过 VenueRepository 统一管理）**：
///
/// - VenuePublicationStats：年度指标集合（来自 OpenAlex）
/// - VenueMesh：MeSH 主题词集合（来自 Serfile）
/// - VenueRelation：期刊关联关系集合（来自 Serfile）
/// - VenueIndexingHistory：索引历史记录集合（来自 Serfile）
///
/// **验证规则**：
///
/// - 所有类型：displayName 和 venueType 必填
/// - 来自 OpenAlex：openalexId 必填
/// - 来自 PubMed：nlmId 或 issnL 至少一个
///
/// **设计说明**：
///
/// - 支持 OpenAlex 的 7 种载体类型（journal/repository/conference 等）
/// - 标识符统一管理（ISSN/ISBN/OpenAlex/NLM/MAG/FATCAT/WIKIDATA/CODEN）
/// - OA 状态和 APC 信息用于开放获取分析
/// - 语言信息支持多语言期刊和摘要语言追踪
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueAggregate extends AggregateRoot<Long> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 核心属性 ==========

  /// 载体类型（必填）
  private final VenueType venueType;

  /// 显示名称（必填）
  private final String displayName;

  /// 缩写标题（ISO 缩写）
  private String abbreviatedTitle;

  /// 替代名称列表
  private List<String> alternateTitles;

  /// 主页 URL
  private String homepageUrl;

  // ========== 冗余标识符（高频查询优化） ==========

  /// OpenAlex ID（格式：S1234567890）
  private String openalexId;

  /// Linking ISSN
  private String issnL;

  /// NLM 唯一标识符（冗余，来自 PubMed Catalog）
  private String nlmId;

  /// DOI 前缀（来自 Crossref）
  private String doiPrefix;

  /// CODEN 编码（6字符标识符，来自 Serfile）
  private String coden;

  // ========== 出版信息 ==========

  /// 出版频率（来自 Serfile，如 Weekly/Monthly/Quarterly）
  private String frequency;

  // ========== 语言信息 ==========

  /// 主要语言代码（ISO 639-3，冗余字段便于查询）
  private String primaryLanguage;

  /// 期刊语言信息（包含主语言和摘要语言列表）
  private VenueLanguages languages;

  // ========== 出版商信息 ==========

  /// 出版商名称（来自 Crossref/DOAJ）
  private String publisher;

  // ========== 出版历史 ==========

  /// 出版历史（创刊/停刊年份）
  private PublicationHistory publicationHistory;

  // ========== 索引收录信息 ==========

  /// MEDLINE 索引收录信息
  private IndexingInfo indexingInfo;

  // ========== 宿主机构 ==========

  /// 宿主机构信息
  private HostOrganization hostOrganization;

  // ========== 地理信息 ==========

  /// 国家代码（ISO 3166-1 alpha-2）
  private String countryCode;

  // ========== OA 状态 ==========

  /// 是否开放获取
  private boolean isOa;

  /// 是否在 DOAJ 中
  private boolean isInDoaj;

  /// 是否为核心期刊
  private boolean isCore;

  /// OA 类型（GOLD/DIAMOND/HYBRID/BRONZE）
  private String oaType;

  // ========== 评级信息 ==========

  /// 最新评级快照（冗余，高频查询优化）
  private LatestRating latestRating;

  // ========== 统计快照 ==========

  /// 当前统计快照
  private VenueStats currentStats;

  // ========== APC 信息 ==========

  /// APC（文章处理费）信息
  private ApcInfo apcInfo;

  // ========== 关联学会 ==========

  /// 关联学会列表
  private List<Society> societies;

  // ========== 数据来源 ==========

  /// 数据来源信息
  private ProvenanceInfo provenance;

  // ========== 聚合内值对象 ==========

  /// 标识符集合
  private final List<VenueIdentifier> identifiers;

  /// 私有构造函数（通过工厂方法创建）。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueType 载体类型
  /// @param displayName 显示名称
  private VenueAggregate(Long id, VenueType venueType, String displayName) {
    super(id);

    Assert.notNull(venueType, "载体类型不能为空");
    Assert.notBlank(displayName, "显示名称不能为空");

    this.venueType = venueType;
    this.displayName = displayName;
    this.identifiers = new ArrayList<>();
    this.alternateTitles = new ArrayList<>();
    this.societies = new ArrayList<>();
  }

  // ========== 工厂方法 ==========

  /// 从 OpenAlex Source 创建载体。
  ///
  /// @param openalexId OpenAlex Source ID（必填）
  /// @param venueType 载体类型
  /// @param displayName 显示名称
  /// @return 载体聚合根
  public static VenueAggregate fromOpenAlex(
      String openalexId, VenueType venueType, String displayName) {
    Assert.notBlank(openalexId, "OpenAlex ID 不能为空");

    VenueAggregate aggregate = new VenueAggregate(null, venueType, displayName);
    aggregate.openalexId = openalexId;

    // 自动添加 OpenAlex 标识符
    aggregate.addIdentifier(VenueIdentifier.forOpenAlex(openalexId));

    // 设置来源信息
    aggregate.provenance = ProvenanceInfo.ofCode(ProvenanceInfo.CODE_OPENALEX);

    return aggregate;
  }

  /// 从 PubMed 创建期刊载体。
  ///
  /// @param displayName 期刊名称
  /// @param nlmId NLM 唯一标识符（nlmId 或 issnL 至少一个必填）
  /// @param issnL Linking ISSN（nlmId 或 issnL 至少一个必填）
  /// @return 载体聚合根
  public static VenueAggregate fromPubMed(String displayName, String nlmId, String issnL) {
    Assert.isTrue(
        StrUtil.isNotBlank(nlmId) || StrUtil.isNotBlank(issnL),
        "来自 PubMed 的载体必须提供 NLM ID 或 ISSN-L");

    VenueAggregate aggregate = new VenueAggregate(null, VenueType.JOURNAL, displayName);

    // 添加标识符并设置冗余字段
    if (StrUtil.isNotBlank(nlmId)) {
      aggregate.nlmId = nlmId;
      aggregate.addIdentifier(VenueIdentifier.forNlm(nlmId));
    }
    if (StrUtil.isNotBlank(issnL)) {
      aggregate.issnL = issnL;
      aggregate.addIdentifier(VenueIdentifier.forIssnL(issnL));
    }

    // 设置来源信息
    aggregate.provenance = ProvenanceInfo.forPubMed();

    return aggregate;
  }

  /// 从持久化状态重建聚合根（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param venueType 载体类型
  /// @param displayName 显示名称
  /// @param version 乐观锁版本
  /// @return 重建的聚合根
  public static VenueAggregate restore(
      Long id, VenueType venueType, String displayName, Long version) {
    VenueAggregate aggregate = new VenueAggregate(id, venueType, displayName);
    aggregate.assignVersion(version);
    return aggregate;
  }

  // ========== 属性设置方法（链式调用） ==========

  /// 设置缩写标题。
  ///
  /// @param abbreviatedTitle 缩写标题
  /// @return 当前对象
  public VenueAggregate withAbbreviatedTitle(String abbreviatedTitle) {
    this.abbreviatedTitle = abbreviatedTitle;
    return this;
  }

  /// 设置替代名称列表。
  ///
  /// @param alternateTitles 替代名称列表
  /// @return 当前对象
  public VenueAggregate withAlternateTitles(List<String> alternateTitles) {
    this.alternateTitles =
        alternateTitles != null ? new ArrayList<>(alternateTitles) : new ArrayList<>();
    return this;
  }

  /// 设置主页 URL。
  ///
  /// @param homepageUrl 主页 URL
  /// @return 当前对象
  public VenueAggregate withHomepageUrl(String homepageUrl) {
    this.homepageUrl = homepageUrl;
    return this;
  }

  /// 设置 OpenAlex ID。
  ///
  /// @param openalexId OpenAlex ID
  /// @return 当前对象
  public VenueAggregate withOpenalexId(String openalexId) {
    this.openalexId = openalexId;
    return this;
  }

  /// 设置 Linking ISSN。
  ///
  /// @param issnL Linking ISSN
  /// @return 当前对象
  public VenueAggregate withIssnL(String issnL) {
    this.issnL = issnL;
    return this;
  }

  /// 设置 NLM 唯一标识符。
  ///
  /// @param nlmId NLM ID
  /// @return 当前对象
  public VenueAggregate withNlmId(String nlmId) {
    this.nlmId = nlmId;
    return this;
  }

  /// 设置 DOI 前缀。
  ///
  /// @param doiPrefix DOI 前缀
  /// @return 当前对象
  public VenueAggregate withDoiPrefix(String doiPrefix) {
    this.doiPrefix = doiPrefix;
    return this;
  }

  /// 设置出版商名称。
  ///
  /// @param publisher 出版商名称
  /// @return 当前对象
  public VenueAggregate withPublisher(String publisher) {
    this.publisher = publisher;
    return this;
  }

  /// 设置出版历史。
  ///
  /// @param publicationHistory 出版历史
  /// @return 当前对象
  public VenueAggregate withPublicationHistory(PublicationHistory publicationHistory) {
    this.publicationHistory = publicationHistory;
    return this;
  }

  /// 设置索引收录信息。
  ///
  /// @param indexingInfo 索引收录信息
  /// @return 当前对象
  public VenueAggregate withIndexingInfo(IndexingInfo indexingInfo) {
    this.indexingInfo = indexingInfo;
    return this;
  }

  /// 设置宿主机构。
  ///
  /// @param hostOrganization 宿主机构
  /// @return 当前对象
  public VenueAggregate withHostOrganization(HostOrganization hostOrganization) {
    this.hostOrganization = hostOrganization;
    return this;
  }

  /// 设置国家代码。
  ///
  /// @param countryCode 国家代码（ISO 3166-1 alpha-2）
  /// @return 当前对象
  public VenueAggregate withCountryCode(String countryCode) {
    this.countryCode = countryCode;
    return this;
  }

  /// 设置 OA 状态。
  ///
  /// @param isOa 是否开放获取
  /// @param isInDoaj 是否在 DOAJ 中
  /// @param isCore 是否为核心期刊
  /// @return 当前对象
  public VenueAggregate withOaStatus(boolean isOa, boolean isInDoaj, boolean isCore) {
    this.isOa = isOa;
    this.isInDoaj = isInDoaj;
    this.isCore = isCore;
    return this;
  }

  /// 设置 OA 类型。
  ///
  /// @param oaType OA 类型（GOLD/DIAMOND/HYBRID/BRONZE）
  /// @return 当前对象
  public VenueAggregate withOaType(String oaType) {
    this.oaType = oaType;
    return this;
  }

  /// 设置最新评级快照。
  ///
  /// @param latestRating 最新评级快照
  /// @return 当前对象
  public VenueAggregate withLatestRating(LatestRating latestRating) {
    this.latestRating = latestRating;
    return this;
  }

  /// 设置当前统计快照。
  ///
  /// @param currentStats 统计快照
  /// @return 当前对象
  public VenueAggregate withCurrentStats(VenueStats currentStats) {
    this.currentStats = currentStats;
    return this;
  }

  /// 设置 APC 信息。
  ///
  /// @param apcInfo APC 信息
  /// @return 当前对象
  public VenueAggregate withApcInfo(ApcInfo apcInfo) {
    this.apcInfo = apcInfo;
    return this;
  }

  /// 设置关联学会列表。
  ///
  /// @param societies 学会列表
  /// @return 当前对象
  public VenueAggregate withSocieties(List<Society> societies) {
    this.societies = societies != null ? new ArrayList<>(societies) : new ArrayList<>();
    return this;
  }

  /// 设置数据来源信息。
  ///
  /// @param provenance 来源信息
  /// @return 当前对象
  public VenueAggregate withProvenance(ProvenanceInfo provenance) {
    this.provenance = provenance;
    return this;
  }

  /// 设置 OpenAlex 来源信息。
  ///
  /// @param sourceCreatedDate 源数据创建日期
  /// @param sourceUpdatedDate 源数据更新日期
  /// @return 当前对象
  public VenueAggregate withOpenAlexProvenance(
      LocalDate sourceCreatedDate, LocalDate sourceUpdatedDate) {
    this.provenance = ProvenanceInfo.forOpenAlex(sourceCreatedDate, sourceUpdatedDate);
    return this;
  }

  /// 设置 CODEN 编码。
  ///
  /// @param coden CODEN 编码（6字符）
  /// @return 当前对象
  public VenueAggregate withCoden(String coden) {
    this.coden = coden;
    return this;
  }

  /// 设置出版频率。
  ///
  /// @param frequency 出版频率（如 Weekly/Monthly/Quarterly）
  /// @return 当前对象
  public VenueAggregate withFrequency(String frequency) {
    this.frequency = frequency;
    return this;
  }

  /// 设置主要语言。
  ///
  /// @param primaryLanguage 主要语言代码（ISO 639-3）
  /// @return 当前对象
  public VenueAggregate withPrimaryLanguage(String primaryLanguage) {
    this.primaryLanguage = primaryLanguage;
    return this;
  }

  /// 设置语言信息。
  ///
  /// @param languages 语言信息值对象
  /// @return 当前对象
  public VenueAggregate withLanguages(VenueLanguages languages) {
    this.languages = languages;
    // 同步更新冗余字段
    if (languages != null && languages.hasPrimaryLanguages()) {
      this.primaryLanguage = languages.getMainLanguage();
    }
    return this;
  }

  // ========== 标识符管理方法 ==========

  /// 添加标识符。
  ///
  /// 如果已存在相同类型和值的标识符，则忽略。
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
  /// @param type 标识符类型
  /// @param value 标识符值
  public void removeIdentifier(VenueIdentifierType type, String value) {
    identifiers.removeIf(i -> i.type() == type && i.value().equals(value));
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

  /// 批量设置标识符（清空现有并添加新的）。
  ///
  /// @param newIdentifiers 新标识符列表
  public void setIdentifiers(List<VenueIdentifier> newIdentifiers) {
    identifiers.clear();
    if (newIdentifiers != null) {
      newIdentifiers.forEach(this::addIdentifier);
    }
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

  /// 判断是否有宿主机构。
  ///
  /// @return true 如果有宿主机构
  public boolean hasHostOrganization() {
    return hostOrganization != null;
  }

  /// 判断是否有统计快照。
  ///
  /// @return true 如果有当前统计快照
  public boolean hasStats() {
    return currentStats != null;
  }

  /// 判断是否来自 OpenAlex。
  ///
  /// @return true 如果来自 OpenAlex
  public boolean isFromOpenAlex() {
    return provenance != null && provenance.isFromOpenAlex();
  }

  /// 判断是否来自 PubMed。
  ///
  /// @return true 如果来自 PubMed
  public boolean isFromPubMed() {
    return provenance != null && provenance.isFromPubMed();
  }

  /// 判断是否有出版商信息。
  ///
  /// @return true 如果有出版商
  public boolean hasPublisher() {
    return StrUtil.isNotBlank(publisher);
  }

  /// 判断是否有出版历史信息。
  ///
  /// @return true 如果有出版历史
  public boolean hasPublicationHistory() {
    return publicationHistory != null;
  }

  /// 判断是否有索引收录信息。
  ///
  /// @return true 如果有索引收录信息
  public boolean hasIndexingInfo() {
    return indexingInfo != null;
  }

  /// 判断期刊是否被 MEDLINE 收录。
  ///
  /// @return true 如果被 MEDLINE 收录
  public boolean isIndexedInMedline() {
    return indexingInfo != null && indexingInfo.isCurrentlyIndexed();
  }

  /// 判断是否有评级信息。
  ///
  /// @return true 如果有最新评级
  public boolean hasRating() {
    return latestRating != null && latestRating.hasRating();
  }

  /// 判断是否为顶级分区期刊（Q1 或 1区）。
  ///
  /// @return true 如果为顶级分区
  public boolean isTopQuartile() {
    return latestRating != null && latestRating.isTopQuartile();
  }

  /// 判断期刊是否已停刊。
  ///
  /// @return true 如果已停刊
  public boolean isCeased() {
    return publicationHistory != null && publicationHistory.ceased();
  }

  /// 判断是否有 CODEN 编码。
  ///
  /// @return true 如果有 CODEN
  public boolean hasCoden() {
    return StrUtil.isNotBlank(coden);
  }

  /// 判断是否有语言信息。
  ///
  /// @return true 如果有语言信息
  public boolean hasLanguages() {
    return languages != null && !languages.isEmpty();
  }

  /// 判断是否为英语期刊。
  ///
  /// @return true 如果主语言为英语
  public boolean isEnglishJournal() {
    return languages != null && languages.isEnglish();
  }

  /// 判断是否为中文期刊。
  ///
  /// @return true 如果主语言为中文
  public boolean isChineseJournal() {
    return languages != null && languages.isChinese();
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

    // 名称不能为空
    if (StrUtil.isBlank(displayName)) {
      throw new IllegalStateException("显示名称不能为空");
    }

    // 来自 OpenAlex 的载体必须有 OpenAlex ID
    if (provenance != null && provenance.isFromOpenAlex() && StrUtil.isBlank(openalexId)) {
      throw new IllegalStateException("来自 OpenAlex 的载体必须提供 OpenAlex ID");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "VenueAggregate[id=%d, type=%s, name=%s, openalexId=%s, issnL=%s]",
        getId(), venueType.getCode(), displayName, openalexId, issnL);
  }
}
