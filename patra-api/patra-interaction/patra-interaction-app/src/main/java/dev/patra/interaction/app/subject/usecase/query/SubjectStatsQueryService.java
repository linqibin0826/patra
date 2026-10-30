package dev.patra.interaction.app.subject.usecase.query;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectRequestException;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.SubjectStats;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.read.SubjectStatsReadPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 批量对象统计（release spec v0.15 决策 C）：文献卡片按一屏一批取收藏人数与讨论数，不要会话。没登记、key 不合法、
/// 没有统计行的对象两项都是 0，不报错。
@Component
@RequiredArgsConstructor
public class SubjectStatsQueryService {

  private final SubjectReadPort subjects;
  private final SubjectStatsReadPort stats;

  /// 按去重后的请求顺序，每个 key 一行。
  ///
  /// @param type 对外类型名
  /// @param keys 对外 key，1 ~ {@value SubjectStatsReadPort#MAX_IDS} 个
  /// @return 计数
  /// @throws InvalidSubjectRequestException 类型未知或既不能收藏也不能讨论；key 个数越界
  public List<SubjectStatsView> stats(String type, List<String> keys) {
    SubjectType subjectType =
        SubjectType.fromSlug(type)
            .filter(
                t -> t.supports(SubjectCapability.COLLECT) || t.supports(SubjectCapability.DISCUSS))
            .orElseThrow(() -> new InvalidSubjectRequestException("不支持的对象类型"));
    if (keys == null || keys.isEmpty() || keys.size() > SubjectStatsReadPort.MAX_IDS) {
      throw new InvalidSubjectRequestException("一次 1 ~ " + SubjectStatsReadPort.MAX_IDS + " 个 key");
    }
    List<String> unique = List.copyOf(new LinkedHashSet<>(keys));
    Map<String, SubjectId> ids = subjects.findIdsByKeys(subjectType, unique);
    List<SubjectId> known = unique.stream().filter(ids::containsKey).map(ids::get).toList();
    Map<SubjectId, SubjectStats> found = known.isEmpty() ? Map.of() : stats.findStats(known);
    return unique.stream()
        .map(
            key -> {
              SubjectId id = ids.get(key);
              SubjectStats s =
                  id == null ? SubjectStats.ZERO : found.getOrDefault(id, SubjectStats.ZERO);
              return SubjectStatsView.of(key, s.collectorCount(), s.discussionCount());
            })
        .toList();
  }
}
