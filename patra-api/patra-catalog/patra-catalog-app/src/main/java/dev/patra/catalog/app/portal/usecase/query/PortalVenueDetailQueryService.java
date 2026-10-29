package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.exception.VenueNotFoundException;
import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel;
import dev.patra.catalog.domain.portal.port.read.VenueDetailReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Portal 期刊详情 CQRS 查询服务。
///
/// 委托 [VenueDetailReadPort] 查询，不存在时抛出 [VenueNotFoundException]。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalVenueDetailQueryService {

  private final VenueDetailReadPort readPort;

  /// 按 ID 查询期刊详情。
  ///
  /// 多次子查询拼装期刊详情，统一只读事务保证一致性和会话边界。
  ///
  /// @param venueId 期刊 ID
  /// @return 期刊详情读模型
  /// @throws VenueNotFoundException 期刊不存在时
  @Transactional(readOnly = true)
  public VenueDetailReadModel getById(long venueId) {
    return readPort.findById(venueId).orElseThrow(() -> new VenueNotFoundException(venueId));
  }
}
