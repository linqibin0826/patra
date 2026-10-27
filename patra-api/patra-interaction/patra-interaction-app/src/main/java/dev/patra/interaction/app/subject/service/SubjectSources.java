package dev.patra.interaction.app.subject.service;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.port.source.SubjectSourcePort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/// 按对象种类选来源策略。构造时校验：每个外部类型恰好一个策略，本地类型不能有策略；缺失、重复或给本地类型配了
/// 策略都让应用启动失败。
@Component
public final class SubjectSources {

  private final Map<SubjectType, SubjectSourcePort> byType = new EnumMap<>(SubjectType.class);

  /// 建索引并校验覆盖。
  ///
  /// @param ports 容器里的全部来源策略
  /// @throws IllegalStateException 外部类型缺策略或有多个策略，或本地类型有策略
  public SubjectSources(List<SubjectSourcePort> ports) {
    for (SubjectSourcePort port : ports) {
      if (port.type().isLocal()) {
        throw new IllegalStateException("本地对象类型不能有来源策略：" + port.type());
      }
      if (byType.putIfAbsent(port.type(), port) != null) {
        throw new IllegalStateException("对象类型有多个来源策略：" + port.type());
      }
    }
    List<SubjectType> missing =
        SubjectType.externalTypes().stream().filter(t -> !byType.containsKey(t)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("对象类型缺少来源策略：" + missing);
    }
  }

  /// 某外部类型的来源策略。
  ///
  /// @param type 对象种类
  /// @return 来源策略
  /// @throws IllegalArgumentException 本地类型（它没有来源，调用方应先判 `isLocal()`）
  public SubjectSourcePort of(SubjectType type) {
    SubjectSourcePort port = byType.get(type);
    if (port == null) {
      throw new IllegalArgumentException("本地对象类型没有来源策略：" + type);
    }
    return port;
  }
}
