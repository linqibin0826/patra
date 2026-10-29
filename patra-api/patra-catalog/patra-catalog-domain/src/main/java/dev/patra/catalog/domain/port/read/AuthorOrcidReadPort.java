package dev.patra.catalog.domain.port.read;

import java.util.Collection;
import java.util.Map;

/// 作者 ORCID 读端口：按 ORCID 批量查作者 ID，供文献导入时软关联作者。
///
/// @author linqibin
/// @since 0.1.0
public interface AuthorOrcidReadPort {

  /// 按 ORCID 批量查作者 ID：只认 ACTIVE 且未软删的作者；输入规模不限，实现自行分片。
  ///
  /// @param orcids 归一化 ORCID
  /// @return ORCID → 作者 ID；未命中的 ORCID 不出现
  Map<String, Long> findAuthorIdsByOrcidIn(Collection<String> orcids);
}
