package com.patra.catalog.domain.model.read.venue;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 期刊详情读模型。
///
/// @param id 期刊主键 ID
/// @param venueType 载体类型（JOURNAL/REPOSITORY/CONFERENCE/等）
/// @param title 期刊标题
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
/// @param openalexId OpenAlex Source ID（可空）
/// @param abbreviatedTitle 缩写标题（可空）
/// @param primaryLanguage 主要语言代码（可空）
/// @param countryCode 国家编码（可空）
/// @param publicationProfile 出版概况（可空）
/// @param citationMetrics 引用指标（可空）
/// @param openAccess 开放获取信息（可空）
/// @param affiliatedSocieties 关联学会列表（可空）
/// @param lastSyncedAt 最后同步时间（可空）
/// @param createdAt 创建时间
/// @param updatedAt 更新时间
@Builder
public record VenueDetailReadModel(
    Long id,
    String venueType,
    String title,
    String issnL,
    String nlmId,
    String openalexId,
    String abbreviatedTitle,
    String primaryLanguage,
    String countryCode,
    PublicationProfile publicationProfile,
    CitationMetrics citationMetrics,
    OpenAccessInfo openAccess,
    List<Society> affiliatedSocieties,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt) {

  /// 构造期刊详情读模型并执行参数校验。
  public VenueDetailReadModel {
    Assert.notNull(id, "期刊 ID 不能为空");
    Assert.notBlank(venueType, "载体类型不能为空");
    Assert.notBlank(title, "期刊标题不能为空");
    Assert.notNull(createdAt, "创建时间不能为空");
    Assert.notNull(updatedAt, "更新时间不能为空");
    // 防御性拷贝：确保集合不可变
    affiliatedSocieties =
        affiliatedSocieties != null ? List.copyOf(affiliatedSocieties) : List.of();
  }
}
