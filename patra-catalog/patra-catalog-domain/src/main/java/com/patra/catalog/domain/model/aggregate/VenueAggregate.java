package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.entity.VenueIdentifier;
import com.patra.catalog.domain.model.entity.VenueMetrics;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueStats;
import com.patra.common.domain.AggregateRoot;
import java.io.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/// 出版载体聚合根。管理期刊、仓库、会议等出版载体的完整信息。
///
/// **聚合边界**：
///
/// - VenueIdentifier（实体，1:N）：标识符集合，与 Venue 生命周期一致
/// - VenueMetrics（实体，1:N）：年度指标集合，与 Venue 生命周期一致
/// - VenueInstance 保持独立（通过 Repository 按需加载）
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
/// - 标识符统一管理（ISSN/ISBN/OpenAlex/NLM/MAG/FATCAT/WIKIDATA）
/// - 年度指标支持时序分析
/// - OA 状态和 APC 信息用于开放获取分析
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

  // ========== 聚合内实体 ==========

  /// 标识符集合
  private final List<VenueIdentifier> identifiers;

  /// 年度指标集合
  private final List<VenueMetrics> yearlyMetrics;

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
    this.yearlyMetrics = new ArrayList<>();
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

    // 添加标识符
    if (StrUtil.isNotBlank(nlmId)) {
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

  // ========== 标识符管理方法 ==========

  /// 添加标识符。
  ///
  /// @param identifier 标识符
  public void addIdentifier(VenueIdentifier identifier) {
    Assert.notNull(identifier, "标识符不能为空");

    // 检查是否已存在相同的标识符
    boolean exists =
        identifiers.stream()
            .anyMatch(
                i ->
                    i.getType() == identifier.getType()
                        && i.getValue().equals(identifier.getValue()));

    if (!exists) {
      identifiers.add(identifier);

      // 如果是首选标识符，取消同类型其他首选
      if (identifier.isPrimary()) {
        identifiers.stream()
            .filter(i -> i.getType() == identifier.getType() && i != identifier)
            .forEach(VenueIdentifier::unmarkAsPrimary);
      }
    }
  }

  /// 添加标识符（便捷方法）。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param isPrimary 是否首选
  public void addIdentifier(VenueIdentifierType type, String value, boolean isPrimary) {
    addIdentifier(VenueIdentifier.create(type, value, isPrimary));
  }

  /// 移除标识符。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  public void removeIdentifier(VenueIdentifierType type, String value) {
    identifiers.removeIf(i -> i.getType() == type && i.getValue().equals(value));
  }

  /// 设置首选标识符。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  public void setPrimaryIdentifier(VenueIdentifierType type, String value) {
    identifiers.stream()
        .filter(i -> i.getType() == type)
        .forEach(
            i -> {
              if (i.getValue().equals(value)) {
                i.markAsPrimary();
              } else {
                i.unmarkAsPrimary();
              }
            });
  }

  /// 获取特定类型的标识符值（首选）。
  ///
  /// @param type 标识符类型
  /// @return 首选标识符值，如果不存在则返回第一个同类型标识符
  public Optional<String> getIdentifier(VenueIdentifierType type) {
    // 优先返回首选
    return identifiers.stream()
        .filter(i -> i.getType() == type && i.isPrimary())
        .map(VenueIdentifier::getValue)
        .findFirst()
        .or(
            () ->
                identifiers.stream()
                    .filter(i -> i.getType() == type)
                    .map(VenueIdentifier::getValue)
                    .findFirst());
  }

  /// 获取特定类型的所有标识符值。
  ///
  /// @param type 标识符类型
  /// @return 标识符值列表
  public List<String> getIdentifiers(VenueIdentifierType type) {
    return identifiers.stream()
        .filter(i -> i.getType() == type)
        .map(VenueIdentifier::getValue)
        .toList();
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

  // ========== 年度指标管理方法 ==========

  /// 添加/更新年度指标。
  ///
  /// @param year 年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  public void setMetrics(int year, int worksCount, int citedByCount) {
    setMetrics(year, worksCount, citedByCount, null);
  }

  /// 添加/更新年度指标（含 OA 作品数）。
  ///
  /// @param year 年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @param oaWorksCount OA 作品数
  public void setMetrics(int year, int worksCount, int citedByCount, Integer oaWorksCount) {
    Optional<VenueMetrics> existing =
        yearlyMetrics.stream().filter(m -> m.getYear() == year).findFirst();

    if (existing.isPresent()) {
      // 更新现有记录
      VenueMetrics metrics = existing.get();
      metrics.updateCounts(worksCount, citedByCount);
      if (oaWorksCount != null) {
        metrics.withOaWorksCount(oaWorksCount);
      }
    } else {
      // 添加新记录
      yearlyMetrics.add(VenueMetrics.create(year, worksCount, citedByCount, oaWorksCount));
    }
  }

  /// 添加年度指标实体。
  ///
  /// @param metrics 年度指标
  public void addMetrics(VenueMetrics metrics) {
    Assert.notNull(metrics, "年度指标不能为空");

    // 检查是否已存在同年份记录
    boolean exists = yearlyMetrics.stream().anyMatch(m -> m.getYear() == metrics.getYear());
    if (exists) {
      // 移除旧记录
      yearlyMetrics.removeIf(m -> m.getYear() == metrics.getYear());
    }
    yearlyMetrics.add(metrics);
  }

  /// 获取特定年份的指标。
  ///
  /// @param year 年份
  /// @return 年度指标
  public Optional<VenueMetrics> getMetrics(int year) {
    return yearlyMetrics.stream().filter(m -> m.getYear() == year).findFirst();
  }

  /// 获取所有年度指标（按年份降序排列）。
  ///
  /// @return 年度指标列表
  public List<VenueMetrics> getAllMetrics() {
    return yearlyMetrics.stream()
        .sorted(Comparator.comparingInt(VenueMetrics::getYear).reversed())
        .toList();
  }

  /// 获取年度指标（不可变视图）。
  ///
  /// @return 年度指标列表
  public List<VenueMetrics> getYearlyMetrics() {
    return Collections.unmodifiableList(yearlyMetrics);
  }

  /// 批量设置年度指标（清空现有并添加新的）。
  ///
  /// @param newMetrics 新年度指标列表
  public void setYearlyMetrics(List<VenueMetrics> newMetrics) {
    yearlyMetrics.clear();
    if (newMetrics != null) {
      newMetrics.forEach(this::addMetrics);
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

  /// 判断是否有统计信息。
  ///
  /// @return true 如果有当前统计或年度指标
  public boolean hasStats() {
    return currentStats != null || !yearlyMetrics.isEmpty();
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
