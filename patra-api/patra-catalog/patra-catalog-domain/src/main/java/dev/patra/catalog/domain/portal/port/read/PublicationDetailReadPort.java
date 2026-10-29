package dev.patra.catalog.domain.portal.port.read;

import dev.patra.catalog.domain.portal.model.read.PublicationDetailReadModel;
import java.util.Optional;

/// 文献详情 CQRS 读端口。
///
/// 由 Infra 层 `PublicationDetailReadAdapter` 实现，按 ID 查询完整文献详情。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDetailReadPort {

  /// 按 ID 查询文献详情。
  ///
  /// @param id 文献 ID
  /// @return 文献详情读模型，不存在时返回 empty
  Optional<PublicationDetailReadModel> findById(long id);
}
