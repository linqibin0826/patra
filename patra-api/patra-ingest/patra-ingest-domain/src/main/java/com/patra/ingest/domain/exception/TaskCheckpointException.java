package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.EnumSet;
import java.util.Set;

/// 任务检查点异常。
/// 
/// 触发场景:当任务检查点的序列化或反序列化失败时抛出,具体包括:
/// 
/// - JSON/二进制转换问题
///   - 缺少必填字段
///   - 版本不兼容(如检查点格式升级后旧数据无法解析)
/// 
/// 处理建议:
/// 
/// - **`PARSE`**:根据幂等性保证,考虑从头开始重启任务或暂停任务等待人工介入。
///   - **`SERIALIZE`**:限次重试;持续失败应告警运维人员以避免进度丢失。
/// 
/// @author linqibin
/// @since 0.1.0
public class TaskCheckpointException extends IngestException implements HasErrorTraits {

  public enum Type {
    /// 解析现有检查点失败。
    PARSE,
    /// 序列化新检查点失败。
    SERIALIZE
  }

  /// 发生的失败类型。
  private final Type type;

  /// 构造任务检查点异常。
/// 
/// @param type 失败类型
/// @param message 描述性消息
/// @param cause 底层异常
  public TaskCheckpointException(Type type, String message, Throwable cause) {
    super(message, cause);
    this.type = type;
  }

  /// 获取失败类型。
/// 
/// @return 类型枚举
  public Type getType() {
    return type;
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return EnumSet.of(ErrorTrait.RULE_VIOLATION);
  }
}
