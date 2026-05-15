package dev.linqibin.patra.ingest.domain.model.vo.execution;

import java.util.Map;

/// 任务参数值对象。
///
/// 表示任务执行期间所需的不可变键值参数,包括上下文、配置或提示信息。
///
/// 不变式: 内部 Map 始终非空且不可变。
///
/// @param values 参数键值对
/// @author linqibin
/// @since 0.1.0
public record TaskParams(Map<String, Object> values) {
  public TaskParams {
    values = values == null ? Map.of() : Map.copyOf(values);
  }

  /// 当不存在参数时返回 `true`。
  public boolean isEmpty() {
    return values.isEmpty();
  }
}
