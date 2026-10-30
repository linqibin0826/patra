package dev.patra.interaction.domain.subject.port.read;

import dev.patra.interaction.domain.subject.model.read.SubjectStats;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.Collection;
import java.util.Map;

/// 对象公开计数的读端（release spec v0.15 决策 C）：实现只经 SQL 读收藏、讨论两个子域的统计表，是本服务唯一一处
/// 跨子域读；底座被这两个子域依赖，不能 import 它们的类。
public interface SubjectStatsReadPort {

  /// 一次最多查多少个对象（与门户批量接口一致）。
  int MAX_IDS = 100;

  /// 按本地 ID 取计数。
  ///
  /// @param ids 本地 ID，最多 {@value #MAX_IDS} 个
  /// @return 本地 ID → 计数；两张统计表里都没有行的对象不在结果里
  /// @throws IllegalArgumentException 超过上限
  Map<SubjectId, SubjectStats> findStats(Collection<SubjectId> ids);
}
