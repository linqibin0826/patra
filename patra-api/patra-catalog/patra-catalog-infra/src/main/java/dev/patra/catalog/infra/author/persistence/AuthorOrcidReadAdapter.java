package dev.patra.catalog.infra.author.persistence;

import dev.patra.catalog.domain.author.port.read.AuthorOrcidReadPort;
import dev.patra.catalog.infra.author.persistence.dao.AuthorOrcidDao;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// 作者 ORCID 读适配器：按 [#ORCID_BATCH_SIZE] 分片查询后合并。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class AuthorOrcidReadAdapter implements AuthorOrcidReadPort {

  /// 分片大小（PostgreSQL 单条语句绑定参数上限 65535）。
  private static final int ORCID_BATCH_SIZE = 5000;

  private final AuthorOrcidDao authorOrcidDao;

  /// 按 ORCID 批量查作者 ID；ORCID 全局唯一，重复键取先到者。
  ///
  /// @param orcids 归一化 ORCID
  /// @return ORCID → 作者 ID
  @Override
  public Map<String, Long> findAuthorIdsByOrcidIn(Collection<String> orcids) {
    List<String> distinct = orcids.stream().distinct().toList();
    Map<String, Long> result = new HashMap<>();
    for (int i = 0; i < distinct.size(); i += ORCID_BATCH_SIZE) {
      List<String> slice = distinct.subList(i, Math.min(i + ORCID_BATCH_SIZE, distinct.size()));
      authorOrcidDao
          .findAuthorIdsByOrcidIn(slice)
          .forEach(view -> result.merge(view.getOrcid(), view.getAuthorId(), (a, b) -> a));
    }
    return result;
  }
}
