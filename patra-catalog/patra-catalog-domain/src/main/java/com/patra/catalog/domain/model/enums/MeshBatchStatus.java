package com.patra.catalog.domain.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MeSH 批次处理状态枚举。
 *
 * <p>定义单个批次的处理状态。
 *
 * <p><b>状态转换规则</b>：
 *
 * <pre>
 * 正常流程：PENDING → PROCESSING → SUCCESS
 * 失败流程：PENDING → PROCESSING → FAILED
 * 重试流程：FAILED → PROCESSING → SUCCESS/FAILED
 * </pre>
 *
 * <p><b>设计说明</b>：
 *
 * <ul>
 *   <li>{@code displayName} - 中文显示名称，用于日志和界面展示
 *   <li>{@code code} - 英文编码，用于数据库存储和 API 交互
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Getter
@AllArgsConstructor
public enum MeshBatchStatus {

  /** 待处理（批次等待处理） */
  PENDING("待处理", "pending"),

  /** 处理中（批次正在处理） */
  PROCESSING("处理中", "processing"),

  /** 成功（批次已成功处理） */
  SUCCESS("成功", "success"),

  /** 失败（批次处理失败） */
  FAILED("失败", "failed");

  /** 状态显示名称（中文） */
  private final String displayName;

  /** 状态编码（用于数据库存储和 API） */
  private final String code;

  /**
   * 根据编码获取枚举。
   *
   * @param code 状态编码
   * @return 对应的枚举值
   * @throws IllegalArgumentException 如果编码无效
   */
  public static MeshBatchStatus fromCode(String code) {
    for (MeshBatchStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("无效的批次状态编码: " + code);
  }

  /**
   * 判断批次是否为终态（成功或失败）。
   *
   * @return true 如果批次已结束
   */
  public boolean isTerminal() {
    return this == SUCCESS || this == FAILED;
  }

  /**
   * 判断批次是否可以重试。
   *
   * @return true 如果批次可以重试（仅 FAILED 状态）
   */
  public boolean canRetry() {
    return this == FAILED;
  }
}
