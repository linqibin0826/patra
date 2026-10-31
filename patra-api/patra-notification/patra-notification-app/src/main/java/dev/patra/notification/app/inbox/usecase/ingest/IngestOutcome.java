package dev.patra.notification.app.inbox.usecase.ingest;

/// 处理一个事件的结果（日志与测试用；三种都确认消息）。
public enum IngestOutcome {

  /// 这个事件处理过了，跳过。
  DUPLICATE,

  /// 评论或经验已删除（墓碑），丢弃。
  DISCARDED,

  /// 已写入信箱。
  APPLIED
}
