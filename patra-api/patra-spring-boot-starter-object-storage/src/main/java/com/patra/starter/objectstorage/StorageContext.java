package com.patra.starter.objectstorage;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/// 解析存储位置时由调用方提供的不可变上下文。
///
/// 作为解析器的单一事实来源,包含:
///
/// - 路径相关输入(业务类型和文件名)
///   - 写入元数据存储的业务标识符
///   - 用于下游分析的可选关联数据
///   - 分区日期
///
@Getter
@Builder(toBuilder = true)
public final class StorageContext {

  private final String businessType;
  private final String filename;
  private final String businessId;

  @Singular("correlationEntry")
  private final Map<String, Object> correlationData;

  @Builder.Default private final LocalDate date = LocalDate.now();

  public LocalDate getDate() {
    return date == null ? LocalDate.now() : date;
  }

  public Map<String, Object> getCorrelationData() {
    return correlationData == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(correlationData);
  }

  /// 验证必填字段并防范路径遍历输入。
  ///
  /// @throws IllegalArgumentException 如果验证失败
  public void validate() {
    require("businessType", businessType);
    require("filename", filename);
    require("businessId", businessId);
    rejectPathSeparator("businessType", businessType);
    rejectPathSeparator("filename", filename);
  }

  private static void require(String field, String value) {
    if (!hasText(value)) {
      throw new IllegalArgumentException(field + " 是必需的");
    }
  }

  private static void rejectPathSeparator(String field, String value) {
    if (value == null) {
      return;
    }
    if (value.contains("/") || value.contains("\\")) {
      throw new IllegalArgumentException(field + " 不能包含路径分隔符");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
