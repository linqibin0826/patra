package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/// 数据来源信息值对象。封装数据的来源和同步状态。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 来源追溯：记录数据来自哪个外部数据源
/// - 时间追踪：记录源数据创建/更新时间及最后同步时间
///
/// 来源代码说明：
///
/// - `OPENALEX`：来自 OpenAlex API/S3 数据
/// - `PUBMED`：来自 PubMed/NLM 数据
/// - `CROSSREF`：来自 Crossref API
/// - `MANUAL`：手动录入
/// - `DOAJ`：来自 DOAJ（开放获取期刊目录）
///
/// 使用示例：
///
/// ```java
/// // 创建 OpenAlex 来源信息
/// ProvenanceInfo provenance = ProvenanceInfo.of(
///     "OPENALEX",
///     LocalDate.of(2012, 1, 1),
///     LocalDate.of(2024, 6, 15),
///     Instant.now()
/// );
///
/// // 仅来源代码（无时间信息）
/// ProvenanceInfo simple = ProvenanceInfo.ofCode("MANUAL");
/// ```
///
/// @param code 来源代码（OPENALEX/PUBMED/CROSSREF/MANUAL/DOAJ）
/// @param sourceCreatedDate 源数据创建日期（可选）
/// @param sourceUpdatedDate 源数据更新日期（可选）
/// @param lastSyncedAt 最后同步时间（可选）
/// @author linqibin
/// @since 0.1.0
public record ProvenanceInfo(
    String code, LocalDate sourceCreatedDate, LocalDate sourceUpdatedDate, Instant lastSyncedAt)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 来源代码：OpenAlex
  public static final String CODE_OPENALEX = "OPENALEX";

  /// 来源代码：PubMed/NLM
  public static final String CODE_PUBMED = "PUBMED";

  /// 来源代码：Crossref
  public static final String CODE_CROSSREF = "CROSSREF";

  /// 来源代码：手动录入
  public static final String CODE_MANUAL = "MANUAL";

  /// 来源代码：DOAJ
  public static final String CODE_DOAJ = "DOAJ";

  /// 紧凑构造器：验证来源信息的有效性。
  ///
  /// @throws IllegalArgumentException 如果来源代码为空
  public ProvenanceInfo {
    Assert.notBlank(code, "来源代码不能为空");
  }

  /// 创建完整来源信息。
  ///
  /// @param code 来源代码
  /// @param sourceCreatedDate 源数据创建日期
  /// @param sourceUpdatedDate 源数据更新日期
  /// @param lastSyncedAt 最后同步时间
  /// @return 来源信息值对象
  public static ProvenanceInfo of(
      String code, LocalDate sourceCreatedDate, LocalDate sourceUpdatedDate, Instant lastSyncedAt) {
    return new ProvenanceInfo(code, sourceCreatedDate, sourceUpdatedDate, lastSyncedAt);
  }

  /// 创建仅来源代码的来源信息（无时间信息）。
  ///
  /// @param code 来源代码
  /// @return 来源信息值对象
  public static ProvenanceInfo ofCode(String code) {
    return new ProvenanceInfo(code, null, null, null);
  }

  /// 创建 OpenAlex 来源信息。
  ///
  /// @param sourceCreatedDate 源数据创建日期
  /// @param sourceUpdatedDate 源数据更新日期
  /// @return 来源信息值对象
  public static ProvenanceInfo forOpenAlex(
      LocalDate sourceCreatedDate, LocalDate sourceUpdatedDate) {
    return new ProvenanceInfo(CODE_OPENALEX, sourceCreatedDate, sourceUpdatedDate, Instant.now());
  }

  /// 创建 PubMed 来源信息。
  ///
  /// @return 来源信息值对象
  public static ProvenanceInfo forPubMed() {
    return new ProvenanceInfo(CODE_PUBMED, null, null, Instant.now());
  }

  /// 创建手动录入来源信息。
  ///
  /// @return 来源信息值对象
  public static ProvenanceInfo forManual() {
    return new ProvenanceInfo(CODE_MANUAL, null, null, Instant.now());
  }

  /// 判断是否来自 OpenAlex。
  ///
  /// @return true 如果来自 OpenAlex
  public boolean isFromOpenAlex() {
    return CODE_OPENALEX.equals(code);
  }

  /// 判断是否来自 PubMed。
  ///
  /// @return true 如果来自 PubMed
  public boolean isFromPubMed() {
    return CODE_PUBMED.equals(code);
  }

  /// 判断是否手动录入。
  ///
  /// @return true 如果手动录入
  public boolean isManual() {
    return CODE_MANUAL.equals(code);
  }

  /// 更新最后同步时间。
  ///
  /// @return 新的来源信息值对象（更新了同步时间）
  public ProvenanceInfo withSyncedNow() {
    return new ProvenanceInfo(code, sourceCreatedDate, sourceUpdatedDate, Instant.now());
  }
}
