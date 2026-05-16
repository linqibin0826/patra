package dev.linqibin.patra.ingest.domain.model.vo.execution;

/// 任务运行检查点值对象,表示任务运行检查点快照.
///
/// 存储用于重启或从检查点恢复流程的增量恢复信息(JSON).
///
/// - `raw`:原始JSON字符串(空白时规范化为`null`)
///
/// Invariant: blank values normalize to `null`; the structure is not parsed until consumption
/// time.
public record TaskRunCheckpoint(String raw) {

  public TaskRunCheckpoint {
    if (raw != null && raw.isBlank()) {
      raw = null;
    }
  }

  /// 创建无状态的空检查点.
  public static TaskRunCheckpoint empty() {
    return new TaskRunCheckpoint(null);
  }

  /// 指示检查点是否包含序列化状态.
  public boolean isPresent() {
    return raw != null && !raw.isBlank();
  }
}
