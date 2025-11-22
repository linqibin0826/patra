package com.patra.catalog.domain.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/// MeSH 导入任务状态枚举。
/// 
/// 定义导入任务的生命周期状态。
/// 
/// **状态转换规则**：
/// 
/// ```
/// 
/// 正常流程：PENDING → PROCESSING → SUCCESS
/// 失败流程：PENDING → PROCESSING → FAILED
/// 重试流程：FAILED → PROCESSING → SUCCESS/FAILED
/// 取消流程：PENDING → CANCELLED
/// 
/// ```
/// 
/// **设计说明**：
/// 
/// - `displayName` - 中文显示名称，用于日志和界面展示
///   - `code` - 英文编码，用于数据库存储和 API 交互
/// 
/// @author linqibin
/// @since 0.2.0
@Getter
@AllArgsConstructor
public enum MeshImportTaskStatus {

  /// 待处理（任务已创建，等待执行）
  PENDING("待处理", "pending"),

  /// 处理中（任务正在执行）
  PROCESSING("处理中", "processing"),

  /// 成功（任务已完成，所有表导入成功）
  SUCCESS("成功", "success"),

  /// 失败（任务失败，遇到不可恢复错误）
  FAILED("失败", "failed"),

  /// 已取消（任务被手动取消）
  CANCELLED("已取消", "cancelled");

  /// 状态显示名称（中文）
  private final String displayName;

  /// 状态编码（用于数据库存储和 API）
  private final String code;

  /// 根据编码获取枚举。
/// 
/// @param code 状态编码
/// @return 对应的枚举值
/// @throws IllegalArgumentException 如果编码无效
  public static MeshImportTaskStatus fromCode(String code) {
    for (MeshImportTaskStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("无效的任务状态编码: " + code);
  }

  /// 判断任务是否为终态（已完成或已失败或已取消）。
/// 
/// @return true 如果任务已结束
  public boolean isTerminal() {
    return this == SUCCESS || this == FAILED || this == CANCELLED;
  }

  /// 判断任务是否可以重试。
/// 
/// @return true 如果任务可以重试（仅 FAILED 状态）
  public boolean canRetry() {
    return this == FAILED;
  }

  /// 判断任务是否可以取消。
/// 
/// @return true 如果任务可以取消（PENDING 或 PROCESSING 状态）
  public boolean canCancel() {
    return this == PENDING || this == PROCESSING;
  }
}
