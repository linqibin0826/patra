package com.patra.catalog.domain.model.vo.venueinstance;

import com.patra.catalog.domain.model.vo.venue.VenueId;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

/// 期刊实例创建参数。
///
/// 封装创建期刊实例所需的所有参数，用于 `VenueInstanceGateway.findOrCreateJournalInstance()`。
///
/// **字段说明**：
///
/// - `venueId`：载体 ID（必填）
/// - `volume`：卷号（可为 null）
/// - `issue`：期号（可为 null）
/// - `publicationYear`：出版年份（必填）
/// - `publicationMonth`：出版月份（可选）
/// - `publicationDay`：出版日期（可选）
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Builder
public class JournalInstanceParams {

  /// 载体 ID（必填）。
  private final VenueId venueId;

  /// 卷号（可为 null）。
  private final String volume;

  /// 期号（可为 null）。
  private final String issue;

  /// 出版年份（必填）。
  private final Integer publicationYear;

  /// 出版月份（可选，1-12）。
  private final Integer publicationMonth;

  /// 出版日期（可选，1-31）。
  private final Integer publicationDay;

  private JournalInstanceParams(
      VenueId venueId,
      String volume,
      String issue,
      Integer publicationYear,
      Integer publicationMonth,
      Integer publicationDay) {
    Objects.requireNonNull(venueId, "venueId must not be null");
    Objects.requireNonNull(publicationYear, "publicationYear must not be null");
    this.venueId = venueId;
    this.volume = volume;
    this.issue = issue;
    this.publicationYear = publicationYear;
    this.publicationMonth = publicationMonth;
    this.publicationDay = publicationDay;
  }
}
