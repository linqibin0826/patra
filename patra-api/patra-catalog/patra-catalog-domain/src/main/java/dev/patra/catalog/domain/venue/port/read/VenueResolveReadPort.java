package dev.patra.catalog.domain.venue.port.read;

import dev.patra.catalog.domain.venue.model.read.VenueResolveReadModel;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// 期刊批量解析 CQRS 读端口：按期刊 ID 或 NLM ID 批量查期刊，软删的照样返回，只认期刊类型。
///
/// 只返回库里存在的期刊，顺序不保证；去重、按请求排序、为不存在的键补行由查询服务负责。
///
/// @author linqibin
/// @since 0.1.0
public interface VenueResolveReadPort {

  /// 按期刊 ID 批量查。
  ///
  /// @param ids 期刊主键；空集合时直接返回空列表、不查库
  /// @return 存在的期刊（含软删）
  List<VenueResolveReadModel> findByIds(Collection<Long> ids);

  /// 按 NLM ID 批量查：主 NLM、副 NLM 都能命中，读模型里的 `nlmId` 总是主 NLM。
  ///
  /// @param nlmIds NLM ID；空集合时直接返回空映射、不查库
  /// @return 命中的 NLM ID（可能是副 NLM）→ 期刊（含软删）
  Map<String, VenueResolveReadModel> findByNlmIds(Collection<String> nlmIds);
}
