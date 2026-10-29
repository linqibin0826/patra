package dev.patra.catalog.domain.portal.port.read;

import dev.patra.catalog.domain.portal.model.read.VenueDetailReadModel;
import java.util.Optional;

/// 期刊详情 CQRS 读端口。
///
/// 由 Infra 层 `VenueDetailReadAdapter` 实现，按 ID 查询完整期刊详情。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueDetailReadPort {

  /// 按 ID 查询期刊详情。
  ///
  /// @param venueId 期刊 ID
  /// @return 期刊详情读模型，不存在时返回 empty
  Optional<VenueDetailReadModel> findById(long venueId);
}
