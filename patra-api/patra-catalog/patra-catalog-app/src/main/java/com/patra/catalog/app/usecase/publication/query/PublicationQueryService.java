package com.patra.catalog.app.usecase.publication.query;

import static com.patra.common.util.StringUtils.escapeLike;
import static com.patra.common.util.StringUtils.trimToNull;

import com.patra.catalog.app.usecase.publication.query.dto.PublicationDetailQuery;
import com.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import com.patra.catalog.domain.exception.PublicationNotFoundException;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import com.patra.catalog.domain.model.read.publication.PublicationFilter;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import com.patra.catalog.domain.port.read.PublicationReadPort;
import com.patra.common.query.PageResult;
import com.patra.common.query.PagingParams;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/// 文献出版物 CQRS 查询服务。
///
/// 负责查询参数归一化和委托 [PublicationReadPort] 执行查询。
/// 无 `@Transactional`（只读操作，JPA 默认读事务即可）。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PublicationQueryService {

  private final PublicationReadPort publicationReadPort;

  /// 分页查询文献列表。
  ///
  /// 归一化分页参数和筛选条件后委托 ReadPort 执行查询。
  ///
  /// @param query 查询参数
  /// @return 分页结果
  public PageResult<PublicationSummaryReadModel> listPublications(PublicationListQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    PagingParams paging = PagingParams.normalize(query.page(), query.pageSize());
    PublicationFilter filter =
        PublicationFilter.builder()
            .keyword(escapeLike(trimToNull(query.q())))
            .yearFrom(query.yearFrom())
            .yearTo(query.yearTo())
            .languageBase(trimToNull(query.languageBase()))
            .isOa(query.isOa())
            .oaStatus(trimToNull(query.oaStatus()))
            .venueId(query.venueId())
            .pmid(trimToNull(query.pmid()))
            .doi(trimToNull(query.doi()))
            .provenanceCode(trimToNull(query.provenanceCode()))
            .publicationStatus(trimToNull(query.publicationStatus()))
            .build();
    return publicationReadPort.findPublicationPage(paging, filter);
  }

  /// 查询文献详情。
  ///
  /// @param query 查询参数
  /// @return 文献详情
  /// @throws PublicationNotFoundException 当文献不存在时
  public PublicationDetailReadModel getPublicationDetail(PublicationDetailQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    return publicationReadPort
        .findPublicationDetail(query.id())
        .orElseThrow(() -> new PublicationNotFoundException(query.id()));
  }
}
