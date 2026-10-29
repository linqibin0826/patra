package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.model.read.PortalPaperReadModel;
import dev.patra.catalog.domain.portal.port.read.PublicationSearchReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Portal 批量文献卡片 CQRS 查询服务（文献库、收藏夹页一屏一次请求）。
///
/// 去重后查一次读端口，按请求顺序排列；不存在与软删的 ID 不返回，门户用 interaction 的快照显示「已失效」。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalPublicationCardQueryService {

  private final PublicationSearchReadPort readPort;

  /// 按 ID 批量取卡片。
  ///
  /// @param ids 文献主键（调用方已校验 1 ~ 100 个正整数）
  /// @return 存在且未软删的卡片，按请求顺序
  @Transactional(readOnly = true)
  public List<PortalPaperReadModel> findByIds(List<Long> ids) {
    List<Long> keys = List.copyOf(new LinkedHashSet<>(ids));
    Map<Long, PortalPaperReadModel> found =
        readPort.findCardsByIds(keys).stream()
            .collect(Collectors.toMap(PortalPaperReadModel::id, Function.identity()));
    return keys.stream().map(found::get).filter(Objects::nonNull).toList();
  }
}
