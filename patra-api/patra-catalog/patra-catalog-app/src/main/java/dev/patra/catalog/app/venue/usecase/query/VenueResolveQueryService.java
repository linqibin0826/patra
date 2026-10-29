package dev.patra.catalog.app.venue.usecase.query;

import dev.patra.catalog.domain.venue.model.read.VenueResolveReadModel;
import dev.patra.catalog.domain.venue.port.read.VenueResolveReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// 期刊批量解析 CQRS 查询服务（给 interaction 的对象登记表，PAP-117）。
///
/// 每个请求键输出一行：按请求顺序、重复键只留第一次，查不到的键 `found` 为 null；软删的期刊照样返回。
/// 按 NLM 时主 NLM、副 NLM 各占一行，都对到同一个期刊。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class VenueResolveQueryService {

  private final VenueResolveReadPort readPort;

  /// 按期刊 ID 批量解析。
  ///
  /// @param ids 期刊主键（调用方已校验 1 ~ 100 个正整数）
  /// @return 每个去重后的键一行，按请求顺序
  @Transactional(readOnly = true)
  public List<VenueResolution> resolveByIds(List<Long> ids) {
    List<Long> keys = List.copyOf(new LinkedHashSet<>(ids));
    Map<Long, VenueResolveReadModel> found =
        readPort.findByIds(keys).stream()
            .collect(Collectors.toMap(VenueResolveReadModel::id, Function.identity()));
    return keys.stream().map(id -> VenueResolution.of(Long.toString(id), found.get(id))).toList();
  }

  /// 按 NLM ID 批量解析。
  ///
  /// @param nlmIds NLM ID（调用方已校验写法与 1 ~ 100 个）
  /// @return 每个去重后的键一行，按请求顺序
  @Transactional(readOnly = true)
  public List<VenueResolution> resolveByNlmIds(List<String> nlmIds) {
    List<String> keys = List.copyOf(new LinkedHashSet<>(nlmIds));
    Map<String, VenueResolveReadModel> found = readPort.findByNlmIds(keys);
    return keys.stream().map(nlmId -> VenueResolution.of(nlmId, found.get(nlmId))).toList();
  }
}
