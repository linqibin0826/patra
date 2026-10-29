package dev.patra.catalog.domain.portal.port.read;

import dev.patra.catalog.domain.portal.model.read.CitationRecord;
import java.util.Collection;
import java.util.List;

/// 引用记录读端口：按文献 ID 批量读出导出与复制引用所需的字段。
///
/// @author linqibin
/// @since 0.1.0
public interface CitationRecordReadPort {

  /// 一次最多读取的 ID 数。
  int MAX_BATCH = 500;

  /// 按 ID 批量读取，只返回存在且未软删的文献，顺序不保证。
  ///
  /// @param ids 文献 ID，最多 [#MAX_BATCH] 个
  /// @return 引用记录；空入参返回空列表
  /// @throws IllegalArgumentException 超过 [#MAX_BATCH] 个时
  List<CitationRecord> findByIds(Collection<Long> ids);
}
