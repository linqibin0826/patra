package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.MediaType;
import com.patra.catalog.domain.model.enums.OaStatus;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import com.patra.common.domain.AggregateRoot;
import com.patra.common.enums.ProvenanceCode;
import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/// 医学文献聚合根。封装出版物的核心元数据及其一致性规则。
///
/// **数据来源**：
///
/// - 主要来源：PubMed（patra-ingest 采集，通过 RocketMQ 发布事件）
///   - 未来支持：EPMC、Crossref、OpenAlex 等多数据源
///   - 数据特点：批量导入、周期性同步、幂等处理
///
/// **一致性边界**：
///
/// - 标识符（PMID/DOI）在同一数据来源中必须唯一
///   - 出版年份必须与载体实例保持一致
///   - OA 状态必须与 OA 位置集合同步
///   - 语言信息遵循三层标准化结构
///
/// **业务规则**：
///
/// - 文献必须关联到具体的载体实例（venue_instance_id）
///   - venue_id 冗余字段避免二级 JOIN，由应用层同步更新
///   - publication_year 冗余字段优化高频查询，由应用层同步更新
///   - is_oa 和 oa_status 冗余字段由 OA 位置管理同步更新
///   - 重复数据（相同 PMID）跳过，不覆盖已有数据
///
/// **状态转换**：
///
/// - 创建时默认为非 OA 状态（is_oa = false）
///   - 被引次数只能增加，不能减少（通过 incrementCitationCount）
///   - 被引次数同步（通过 syncCitationCount）由定期任务更新
///   - OA 状态由外部 OA 位置管理器更新
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class PublicationAggregate extends AggregateRoot<PublicationId> {

  @Serial private static final long serialVersionUID = 1L;

  // ========== 数据来源追踪 ==========

  /// 数据来源代码（PUBMED, EPMC, CROSSREF 等）
  private final ProvenanceCode provenanceCode;

  /// 最后同步时间（用于增量更新判断）
  private Instant lastSyncedAt;

  // ========== 标识符 ==========

  /// 标识符值对象（PMID, DOI, PMC 等）
  private final PublicationIdentifiers identifiers;

  // ========== 关联关系（含冗余） ==========

  /// 关联的出版载体 ID（冗余优化 - 避免二级 JOIN）
  private final Long venueId;

  /// 关联的载体实例 ID（外键：cat_venue_instance.id）
  private final Long venueInstanceId;

  // ========== 标题和语言 ==========

  /// 文献标题（英文或原语言）
  private final String title;

  /// 原始语言标题（非英文时填充）
  private final String originalTitle;

  /// 语言信息值对象（三层设计：raw → code → base）
  private final LanguageInfo languageInfo;

  // ========== 出版信息 ==========

  /// 出版状态
  private final PublicationStatus publicationStatus;

  /// 媒介类型
  private final MediaType mediaType;

  /// 出版年份（冗余优化 - 最高频查询字段）
  private final Integer publicationYear;

  // ========== OA 信息（冗余 - 快速筛选） ==========

  /// 是否有 OA 版本（冗余 - 快速筛选）
  private Boolean isOa;

  /// 最佳 OA 状态（冗余 - gold/green/hybrid/bronze/closed）
  private OaStatus oaStatus;

  // ========== 统计信息 ==========

  /// 作者列表是否完整（0=不完整，1=完整）
  private final Boolean authorsComplete;

  /// 被引次数（定期更新）
  private Integer citationCount;

  /// 参考文献数量
  private Integer numberOfReferences;

  // ========== 其他信息 ==========

  /// 利益冲突声明
  private final String conflictOfInterest;

  /// 私有构造函数，确保只能通过工厂方法创建聚合根。
  ///
  /// @param id 主键标识（新建时为 null）
  /// @param provenanceCode 数据来源代码
  /// @param identifiers 标识符值对象
  /// @param venueId 载体 ID
  /// @param venueInstanceId 载体实例 ID
  /// @param title 标题
  /// @param originalTitle 原始标题
  /// @param languageInfo 语言信息
  /// @param publicationStatus 出版状态
  /// @param mediaType 媒介类型
  /// @param publicationYear 出版年份
  /// @param isOa 是否 OA
  /// @param oaStatus OA 状态
  /// @param authorsComplete 作者完整性
  /// @param citationCount 被引次数
  /// @param numberOfReferences 参考文献数
  /// @param conflictOfInterest 利益冲突
  /// @param lastSyncedAt 最后同步时间
  private PublicationAggregate(
      PublicationId id,
      ProvenanceCode provenanceCode,
      PublicationIdentifiers identifiers,
      Long venueId,
      Long venueInstanceId,
      String title,
      String originalTitle,
      LanguageInfo languageInfo,
      PublicationStatus publicationStatus,
      MediaType mediaType,
      Integer publicationYear,
      Boolean isOa,
      OaStatus oaStatus,
      Boolean authorsComplete,
      Integer citationCount,
      Integer numberOfReferences,
      String conflictOfInterest,
      Instant lastSyncedAt) {
    super(id);

    // 必填字段验证
    Assert.notNull(provenanceCode, "数据来源代码不能为空");
    Assert.notNull(identifiers, "标识符不能为空");
    Assert.notNull(venueInstanceId, "载体实例ID不能为空");
    Assert.notBlank(title, "标题不能为空");
    Assert.notNull(publicationYear, "出版年份不能为空");

    // 业务规则验证
    Assert.isTrue(
        publicationYear >= 1800 && publicationYear <= 2100,
        "出版年份必须在1800-2100范围内：%d",
        publicationYear);

    // 赋值
    this.provenanceCode = provenanceCode;
    this.lastSyncedAt = lastSyncedAt;
    this.identifiers = identifiers;
    this.venueId = venueId;
    this.venueInstanceId = venueInstanceId;
    this.title = title;
    this.originalTitle = originalTitle;
    this.languageInfo = languageInfo;
    this.publicationStatus = publicationStatus;
    this.mediaType = mediaType;
    this.publicationYear = publicationYear;
    this.isOa = Objects.requireNonNullElse(isOa, false);
    this.oaStatus = oaStatus;
    this.authorsComplete = Objects.requireNonNullElse(authorsComplete, true);
    this.citationCount = Objects.requireNonNullElse(citationCount, 0);
    this.numberOfReferences = Objects.requireNonNullElse(numberOfReferences, 0);
    this.conflictOfInterest = conflictOfInterest;
  }

  /// 创建全新的文献聚合根（用于新采集的文献）。
  ///
  /// 使用场景：patra-ingest 通过 RocketMQ 发布文献创建事件，patra-catalog 消费事件时调用此方法。
  ///
  /// @param provenanceCode 数据来源代码（PUBMED, EPMC, CROSSREF 等）
  /// @param identifiers 标识符值对象
  /// @param venueId 载体 ID
  /// @param venueInstanceId 载体实例 ID
  /// @param title 标题
  /// @param originalTitle 原始标题
  /// @param languageInfo 语言信息
  /// @param publicationStatus 出版状态
  /// @param mediaType 媒介类型
  /// @param publicationYear 出版年份
  /// @param authorsComplete 作者列表完整性
  /// @param numberOfReferences 参考文献数量
  /// @param conflictOfInterest 利益冲突声明
  /// @return 新创建的文献聚合根
  public static PublicationAggregate create(
      ProvenanceCode provenanceCode,
      PublicationIdentifiers identifiers,
      Long venueId,
      Long venueInstanceId,
      String title,
      String originalTitle,
      LanguageInfo languageInfo,
      PublicationStatus publicationStatus,
      MediaType mediaType,
      Integer publicationYear,
      Boolean authorsComplete,
      Integer numberOfReferences,
      String conflictOfInterest) {
    return new PublicationAggregate(
        null, // 新建时 ID 为 null
        provenanceCode,
        identifiers,
        venueId,
        venueInstanceId,
        title,
        originalTitle,
        languageInfo,
        publicationStatus,
        mediaType,
        publicationYear,
        false, // 初始无 OA
        null, // 初始无 OA 状态
        authorsComplete,
        0, // 初始引用数为 0
        numberOfReferences,
        conflictOfInterest,
        Instant.now() // 创建时记录同步时间
        );
  }

  /// 从持久化状态重建已存在的文献聚合根（由仓储层使用）。
  ///
  /// @param id 主键标识
  /// @param provenanceCode 数据来源代码
  /// @param identifiers 标识符值对象
  /// @param venueId 载体 ID
  /// @param venueInstanceId 载体实例 ID
  /// @param title 标题
  /// @param originalTitle 原始标题
  /// @param languageInfo 语言信息
  /// @param publicationStatus 出版状态
  /// @param mediaType 媒介类型
  /// @param publicationYear 出版年份
  /// @param isOa 是否 OA
  /// @param oaStatus OA 状态
  /// @param authorsComplete 作者完整性
  /// @param citationCount 被引次数
  /// @param numberOfReferences 参考文献数
  /// @param conflictOfInterest 利益冲突
  /// @param lastSyncedAt 最后同步时间
  /// @param version 乐观锁版本
  /// @return 从持久化重建的文献聚合根
  public static PublicationAggregate restore(
      PublicationId id,
      ProvenanceCode provenanceCode,
      PublicationIdentifiers identifiers,
      Long venueId,
      Long venueInstanceId,
      String title,
      String originalTitle,
      LanguageInfo languageInfo,
      PublicationStatus publicationStatus,
      MediaType mediaType,
      Integer publicationYear,
      Boolean isOa,
      OaStatus oaStatus,
      Boolean authorsComplete,
      Integer citationCount,
      Integer numberOfReferences,
      String conflictOfInterest,
      Instant lastSyncedAt,
      Long version) {
    PublicationAggregate aggregate =
        new PublicationAggregate(
            id,
            provenanceCode,
            identifiers,
            venueId,
            venueInstanceId,
            title,
            originalTitle,
            languageInfo,
            publicationStatus,
            mediaType,
            publicationYear,
            isOa,
            oaStatus,
            authorsComplete,
            citationCount,
            numberOfReferences,
            conflictOfInterest,
            lastSyncedAt);
    aggregate.assignVersion(version);
    return aggregate;
  }

  // ========== 业务行为方法 ==========

  /// 更新 OA 状态（由 OA 位置管理触发）。
  ///
  /// 业务规则：
  ///
  /// - OA 状态变更会触发领域事件
  ///   - 同步更新 is_oa 和 oa_status 字段
  ///
  /// @param isOa 是否有 OA 版本
  /// @param oaStatus 最佳 OA 状态
  public void updateOaStatus(Boolean isOa, OaStatus oaStatus) {
    OaStatus oldStatus = this.oaStatus;
    this.isOa = Objects.requireNonNullElse(isOa, false);
    this.oaStatus = oaStatus;

    // TODO: 发布 OA 状态变更事件
    // if (!Objects.equals(oldStatus, oaStatus)) {
    //     addDomainEvent(new OaStatusChangedEvent(getId(), oldStatus, oaStatus));
    // }
  }

  /// 增加被引次数。
  ///
  /// 业务规则：被引次数只能增加，不能减少。
  ///
  /// @param increment 增量（必须 > 0）
  /// @throws IllegalArgumentException 如果增量 <= 0
  public void incrementCitationCount(int increment) {
    Assert.isTrue(increment > 0, "被引次数增量必须大于0：%d", increment);
    this.citationCount += increment;
  }

  /// 同步被引次数（用于定期全量同步）。
  ///
  /// **包私有方法**：仅供 Repository 层调用，外部不应直接"设置"被引数。
  ///
  /// 使用场景：定期任务从外部API（如PubMed）同步最新的被引次数。
  ///
  /// @param newCount 新的被引次数（必须 >= 0）
  /// @throws IllegalArgumentException 如果新计数 < 0
  void syncCitationCount(int newCount) {
    Assert.isTrue(newCount >= 0, "被引次数不能为负数：%d", newCount);
    this.citationCount = newCount;
  }

  /// 同步参考文献数量（用于定期全量同步）。
  ///
  /// **包私有方法**：仅供 Repository 层调用，外部不应直接"设置"参考文献数。
  ///
  /// 使用场景：从外部数据源同步参考文献数量。
  ///
  /// @param count 参考文献数量（必须 >= 0）
  /// @throws IllegalArgumentException 如果数量 < 0
  void syncNumberOfReferences(int count) {
    Assert.isTrue(count >= 0, "参考文献数量不能为负数：%d", count);
    this.numberOfReferences = count;
  }

  /// 同步最后同步时间（由仓储层调用）。
  ///
  /// **包私有方法**：仅供 Repository 层调用。
  ///
  /// 使用场景：Repository 在保存聚合时自动更新同步时间。
  ///
  /// @param syncedAt 同步时间
  void syncLastSyncedAt(Instant syncedAt) {
    this.lastSyncedAt = syncedAt;
  }

  // ========== 便捷访问器 ==========

  /// 获取 PMID（便捷访问器）。
  ///
  /// @return PMID 值或 null
  public String getPmid() {
    return identifiers.pmid();
  }

  /// 获取 DOI（便捷访问器）。
  ///
  /// @return DOI 值或 null
  public String getDoi() {
    return identifiers.doi();
  }

  /// 判断是否为开放获取文献。
  ///
  /// @return true 如果有任何形式的 OA 版本
  public boolean isOpenAccess() {
    return Boolean.TRUE.equals(isOa);
  }

  /// 判断是否为黄金 OA。
  ///
  /// @return true 如果为黄金 OA
  public boolean isGoldOa() {
    return oaStatus != null && oaStatus.isGold();
  }

  /// 判断是否有原始语言标题。
  ///
  /// @return true 如果有原始标题
  public boolean hasOriginalTitle() {
    return StrUtil.isNotBlank(originalTitle);
  }

  /// 判断作者列表是否完整。
  ///
  /// @return true 如果作者列表完整
  public boolean hasCompleteAuthors() {
    return Boolean.TRUE.equals(authorsComplete);
  }

  // ========== 不变量验证 ==========

  /// 验证聚合根的业务不变量。
  ///
  /// @throws IllegalStateException 如果不变量被违反
  @Override
  protected void assertInvariants() {
    // 标识符不能为空
    if (identifiers == null) {
      throw new IllegalStateException("文献标识符不能为空");
    }

    // 标题不能为空
    if (StrUtil.isBlank(title)) {
      throw new IllegalStateException("文献标题不能为空");
    }

    // 出版年份必须在合理范围内
    if (publicationYear == null || publicationYear < 1800 || publicationYear > 2100) {
      throw new IllegalStateException("出版年份必须在1800-2100范围内");
    }

    // 载体实例ID不能为空
    if (venueInstanceId == null) {
      throw new IllegalStateException("载体实例ID不能为空");
    }

    // 被引次数不能为负数
    if (citationCount < 0) {
      throw new IllegalStateException("被引次数不能为负数");
    }

    // OA 状态一致性：isOa=true 时必须有 oaStatus
    if (Boolean.TRUE.equals(isOa) && oaStatus == null) {
      throw new IllegalStateException("OA 文献必须指定 OA 状态");
    }
  }

  @Override
  public String toString() {
    return String.format(
        "PublicationAggregate[id=%d, provenance=%s, pmid=%s, doi=%s, title=%s, year=%d]",
        getId(),
        provenanceCode,
        getPmid(),
        getDoi(),
        title != null && title.length() > 50 ? title.substring(0, 50) + "..." : title,
        publicationYear);
  }
}
