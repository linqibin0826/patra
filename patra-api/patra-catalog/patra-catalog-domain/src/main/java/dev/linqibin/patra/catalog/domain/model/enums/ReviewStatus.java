package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.util.Locale;
import lombok.Getter;

/// 审核状态枚举。
///
/// 字段映射：cat_publication_metadata.review_status
///
/// 数据质量审核状态说明：
///
/// - **PENDING** - 待审核（新导入数据，尚未人工审核）
/// - **REVIEWED** - 已审核（已完成人工审核，无论结果如何）
/// - **APPROVED** - 已批准（审核通过，数据质量合格）
/// - **REJECTED** - 已拒绝（审核不通过，需要修正或删除）
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum ReviewStatus {

  /// 待审核
  PENDING("pending", "Pending Review"),

  /// 已审核
  REVIEWED("reviewed", "Reviewed"),

  /// 已批准
  APPROVED("approved", "Approved"),

  /// 已拒绝
  REJECTED("rejected", "Rejected");

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  ReviewStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "pending", "APPROVED"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static ReviewStatus fromCode(String value) {
    Assert.notBlank(value, "审核状态代码不能为空");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (ReviewStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的审核状态：" + value);
  }

  /// 判断是否已完成审核。
  ///
  /// @return true 如果不是 PENDING
  public boolean isCompleted() {
    return this != PENDING;
  }

  /// 判断是否通过审核。
  ///
  /// @return true 如果为 APPROVED
  public boolean isPassed() {
    return this == APPROVED;
  }
}
