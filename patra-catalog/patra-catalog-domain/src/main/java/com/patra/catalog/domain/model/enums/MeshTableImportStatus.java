package com.patra.catalog.domain.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/// MeSH 表导入状态枚举。
///
/// 定义单张表的导入状态。
///
/// **状态转换规则**：
///
/// ```
///
/// 正常流程：NOT_STARTED → IN_PROGRESS → COMPLETED
/// 失败流程：NOT_STARTED → IN_PROGRESS → FAILED
///
/// ```
///
/// **设计说明**：
///
/// - `displayName` - 中文显示名称，用于日志和界面展示
///   - `code` - 英文编码，用于数据库存储和 API 交互
///
/// @author linqibin
/// @since 0.1.0
@Getter
@AllArgsConstructor
public enum MeshTableImportStatus {

  /// 未开始（表尚未开始处理）
  NOT_STARTED("未开始", "not_started"),

  /// 进行中（表正在处理）
  IN_PROGRESS("进行中", "in_progress"),

  /// 已完成（表已成功导入）
  COMPLETED("已完成", "completed"),

  /// 失败（表导入失败）
  FAILED("失败", "failed");

  /// 状态显示名称（中文）
  private final String displayName;

  /// 状态编码（用于数据库存储和 API）
  private final String code;

  /// 根据编码获取枚举。
  ///
  /// @param code 状态编码
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果编码无效
  public static MeshTableImportStatus fromCode(String code) {
    for (MeshTableImportStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("无效的表导入状态编码: " + code);
  }

  /// 判断表是否已完成（成功或失败）。
  ///
  /// @return true 如果表导入已结束
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED;
  }

  /// 判断表是否正在处理。
  ///
  /// @return true 如果表正在处理
  public boolean isInProgress() {
    return this == IN_PROGRESS;
  }
}
