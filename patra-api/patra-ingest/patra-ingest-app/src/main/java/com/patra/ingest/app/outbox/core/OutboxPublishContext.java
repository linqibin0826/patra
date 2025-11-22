package com.patra.ingest.app.outbox.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/// Outbox 发布上下文,封装聚合和元数据。
///
/// 使用类型安全的上下文映射替代可变长度参数。
///
/// 此类是不可变且线程安全的。所有上下文数据都进行了防御性复制。
///
/// ### 使用示例
///
/// ```java
/// OutboxPublishContext ctx = OutboxPublishContext.builder()
///     .put("plan", planAggregate)
///     .put("schedule", scheduleAggregate)
///     .put("traceId", traceContext.getTraceId())
///     .build();
///
/// publisher.publish(events, ctx);
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class OutboxPublishContext {

  private final Map<String, Object> contextData;

  /// 私有构造函数,强制使用构建器。
  ///
  /// @param contextData 上下文数据映射(防御性复制)
  private OutboxPublishContext(Map<String, Object> contextData) {
    this.contextData = new HashMap<>(contextData);
  }

  /// 通过键检索上下文值并进行类型转换。
  ///
  /// @param key 上下文键(不能为 null)
  /// @param type 期望的值类型(不能为 null)
  /// @param <T> 类型参数
  /// @return 转换为指定类型的值,如果未找到键则返回 null
  /// @throws ClassCastException 如果值存在但无法转换为期望类型
  @SuppressWarnings("unchecked")
  public <T> T get(String key, Class<T> type) {
    Object value = contextData.get(key);
    if (value == null) {
      return null;
    }
    if (!type.isInstance(value)) {
      throw new ClassCastException(
          String.format(
              "键 '%s' 的上下文值不是类型 %s,实际类型: %s", key, type.getName(), value.getClass().getName()));
    }
    return (T) value;
  }

  /// 通过键检索上下文值并进行类型转换,提供默认值回退。
  ///
  /// @param key 上下文键
  /// @param type 期望类型
  /// @param defaultValue 如果未找到键则使用的默认值
  /// @param <T> 类型参数
  /// @return 值或默认值
  public <T> T getOrDefault(String key, Class<T> type, T defaultValue) {
    T value = get(key, type);
    return value != null ? value : defaultValue;
  }

  /// 创建用于构造上下文的构建器。
  ///
  /// @return 构建器实例
  public static Builder builder() {
    return new Builder();
  }

  /// {@link OutboxPublishContext} 的构建器。
  public static final class Builder {
    private final Map<String, Object> data = new HashMap<>();

    private Builder() {}

    /// 向上下文添加键值对。
    ///
    /// @param key 上下文键(不能为 null)
    /// @param value 上下文值
    /// @return 此构建器
    public Builder put(String key, Object value) {
      Objects.requireNonNull(key, "上下文键不能为 null");
      data.put(key, value);
      return this;
    }

    /// 构建不可变上下文。
    ///
    /// @return OutboxPublishContext 实例
    public OutboxPublishContext build() {
      return new OutboxPublishContext(data);
    }
  }
}
