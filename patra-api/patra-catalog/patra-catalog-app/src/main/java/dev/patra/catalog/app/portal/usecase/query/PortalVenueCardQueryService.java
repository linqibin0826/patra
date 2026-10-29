package dev.patra.catalog.app.portal.usecase.query;

import dev.patra.catalog.domain.portal.model.read.VenueBrowseReadModel;
import dev.patra.catalog.domain.portal.port.read.VenueBrowseReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Portal 批量期刊卡片 CQRS 查询服务（用户主页「投稿经验」等列表一屏一次请求，PAP-117）。
///
/// 去重后查一次读端口，按请求顺序排列；不存在、软删与非期刊的 ID 不返回，门户用 interaction 的快照显示。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class PortalVenueCardQueryService {

  private final VenueBrowseReadPort readPort;

  /// 按 ID 批量取期刊卡片。
  ///
  /// @param ids 期刊主键（调用方已校验 1 ~ 100 个正整数）
  /// @return 存在且未软删的期刊卡片，按请求顺序
  @Transactional(readOnly = true)
  public List<VenueBrowseReadModel> findByIds(List<Long> ids) {
    List<Long> keys = List.copyOf(new LinkedHashSet<>(ids));
    Map<Long, VenueBrowseReadModel> found =
        readPort.findCardsByIds(keys).stream()
            .collect(Collectors.toMap(VenueBrowseReadModel::id, Function.identity()));
    return keys.stream().map(found::get).filter(Objects::nonNull).toList();
  }
}
