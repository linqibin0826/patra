package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.exception.PublicationNotFoundException;
import dev.patra.catalog.domain.portal.model.read.PublicationDetailReadModel;
import dev.patra.catalog.domain.portal.port.read.PublicationDetailReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Portal 文献详情 CQRS 查询服务。
///
/// 委托 [PublicationDetailReadPort] 查询，不存在时抛出 [PublicationNotFoundException]。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalPublicationDetailQueryService {

  private final PublicationDetailReadPort readPort;

  /// 按 ID 查询文献详情。
  ///
  /// 多次子查询拼装文献详情，统一只读事务保证一致性和会话边界。
  ///
  /// @param id 文献 ID
  /// @return 文献详情读模型
  /// @throws PublicationNotFoundException 文献不存在时
  @Transactional(readOnly = true)
  public PublicationDetailReadModel getById(long id) {
    return readPort.findById(id).orElseThrow(() -> new PublicationNotFoundException(id));
  }
}
