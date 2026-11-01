package dev.patra.analytics.app.collect.usecase.collect;

import dev.patra.tool.cqrs.Command;
import java.util.Objects;

/// 采集一批事件。
///
/// @param body 请求体原文（`collect-batch`）
public record CollectEventsCommand(String body) implements Command<CollectEventsResult> {

  /// 请求体必填。
  public CollectEventsCommand {
    Objects.requireNonNull(body, "body");
  }

  /// 创建命令。
  ///
  /// @param body 请求体原文
  /// @return 命令
  public static CollectEventsCommand of(String body) {
    return new CollectEventsCommand(body);
  }
}
