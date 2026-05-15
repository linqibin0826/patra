package dev.linqibin.patra.objectstorage.domain.model.enums;

/// 存储文件的生命周期状态。
///
/// 表示文件在对象存储系统中的状态,用于追踪文件从活跃到过期或删除的生命周期变化。
public enum FileStatus {
  /// 活跃状态 - 文件正常可用
  ACTIVE,

  /// 已过期 - 文件超过保留期限
  EXPIRED,

  /// 已删除 - 文件已被软删除
  DELETED
}
