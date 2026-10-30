package dev.patra.interaction.adapter.subject.internal;

import dev.patra.interaction.api.subject.dto.SubjectViewResp;
import dev.patra.interaction.api.subject.dto.SubjectViewsReq;
import dev.patra.interaction.api.subject.dto.SubjectViewsResp;
import dev.patra.interaction.api.subject.endpoint.SubjectEndpoint;
import dev.patra.interaction.app.subject.usecase.query.SubjectViewQueryService;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// 对象视图的内部接口（`POST /_internal/subjects/views`，PAP-143），给通知服务读时现查。网关拒绝 `/*/_internal/**`，
/// 只有服务之间直连能调。
@RestController
@RequiredArgsConstructor
public class SubjectEndpointImpl implements SubjectEndpoint {

  private final SubjectViewQueryService query;

  /// 批量取对象视图。
  ///
  /// @param request 登记表 ID
  /// @return 视图，按去重后的请求顺序
  @Override
  public SubjectViewsResp views(SubjectViewsReq request) {
    return SubjectViewsResp.of(
        query.views(request.subjectIds()).stream().map(SubjectEndpointImpl::toResp).toList());
  }

  /// 视图 → 响应。快照与所有者可能为空（外部对象没有所有者；快照只在登记时没取到来源时为空）。
  ///
  /// @param view 视图
  /// @return 响应
  private static SubjectViewResp toResp(SubjectView view) {
    SubjectSnapshot snapshot = view.snapshot();
    SubjectOwner owner = view.owner();
    return SubjectViewResp.builder()
        .subjectId(view.subjectId().value())
        .type(view.type().name())
        .key(view.key())
        .available(view.state() == SubjectState.ACTIVE)
        .title(snapshot == null ? null : snapshot.title())
        .journal(snapshot == null ? null : snapshot.journal())
        .publishedDate(snapshot == null ? null : snapshot.publishedDate())
        .abbreviation(snapshot == null ? null : snapshot.abbreviation())
        .ownerId(owner == null ? null : owner.userId())
        .ownerAnonymous(owner != null && owner.anonymous())
        .build();
  }
}
