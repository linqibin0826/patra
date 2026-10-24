package dev.patra.interaction.app.subject.service;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/// 按对象种类选来源策略。构造时校验每个 {@link SubjectType} 恰好一个策略，缺失或重复都让应用启动失败。
@Component
public final class SubjectSources {

  private final Map<SubjectType, SubjectSourcePort> byType = new EnumMap<>(SubjectType.class);

  /// 建索引并校验全覆盖。
  ///
  /// @param ports 容器里的全部来源策略
  /// @throws IllegalStateException 某类型缺策略或有多个策略
  public SubjectSources(List<SubjectSourcePort> ports) {
    for (SubjectSourcePort port : ports) {
      if (byType.putIfAbsent(port.type(), port) != null) {
        throw new IllegalStateException("对象类型有多个来源策略：" + port.type());
      }
    }
    List<SubjectType> missing =
        Arrays.stream(SubjectType.values()).filter(t -> !byType.containsKey(t)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("对象类型缺少来源策略：" + missing);
    }
  }

  /// 某类型的来源策略。
  ///
  /// @param type 对象种类
  /// @return 来源策略
  public SubjectSourcePort of(SubjectType type) {
    return byType.get(type);
  }
}
