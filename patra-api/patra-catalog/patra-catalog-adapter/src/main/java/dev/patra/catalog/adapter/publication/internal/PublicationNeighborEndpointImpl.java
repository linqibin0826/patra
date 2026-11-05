package dev.patra.catalog.adapter.publication.internal;

import dev.patra.catalog.api.publication.dto.PublicationLatestResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborReasonResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborResp;
import dev.patra.catalog.api.publication.dto.SubjectNeighborsResp;
import dev.patra.catalog.api.publication.endpoint.PublicationNeighborEndpoint;
import dev.patra.catalog.app.publication.usecase.query.PublicationNeighborQueryService;
import dev.patra.catalog.domain.publication.model.read.SubjectNeighborReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/// 文献邻居内部端点：`GET /_internal/publications/latest`、`GET
// /_internal/publications/{id}/subject-neighbors`。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403）。参数校验写在契约接口的参数上。
///
/// @author linqibin
/// @since 0.1.0
@Validated
@RestController
@RequiredArgsConstructor
public class PublicationNeighborEndpointImpl implements PublicationNeighborEndpoint {

  private final PublicationNeighborQueryService publicationNeighborQueryService;

  @Override
  public PublicationLatestResp latest(int limit) {
    return PublicationLatestResp.of(publicationNeighborQueryService.latest(limit));
  }

  @Override
  public SubjectNeighborsResp subjectNeighbors(long id, int limit) {
    return SubjectNeighborsResp.of(
        publicationNeighborQueryService.subjectNeighbors(id, limit).stream()
            .map(PublicationNeighborEndpointImpl::toItem)
            .toList());
  }

  /// 邻居读模型 → 响应行。
  ///
  /// @param model 读模型
  /// @return 响应行
  private static SubjectNeighborResp toItem(SubjectNeighborReadModel model) {
    return SubjectNeighborResp.builder()
        .id(model.id())
        .reason(SubjectNeighborReasonResp.valueOf(model.reason().name()))
        .meshDescriptorUi(model.meshDescriptorUi())
        .meshTerm(model.meshTerm())
        .venueName(model.venueName())
        .build();
  }
}
