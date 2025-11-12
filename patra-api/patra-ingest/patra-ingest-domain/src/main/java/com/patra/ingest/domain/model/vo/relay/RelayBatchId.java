package com.patra.ingest.domain.model.vo.relay;

import cn.hutool.core.util.IdUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 中继批次 ID Value Object。
 *
 * <p>用于将同一作业执行的所有中继日志分组,支持批次级别的统计和故障排查。
 *
 * <p><b>格式:</b> {@code yyyyMMddHHmmss-xxxxxxxx}
 *
 * <ul>
 *   <li>yyyyMMddHHmmss: UTC 时间戳(中继批次触发时间)
 *   <li>xxxxxxxx: 8 位随机十六进制 UUID(保证唯一性)
 * </ul>
 *
 * <p><b>示例:</b> {@code 20251031150000-a1b2c3d4}
 *
 * <p><b>业务用途:</b>
 *
 * <ul>
 *   <li>将同一作业执行的所有中继日志分组
 *   <li>支持批次级别的统计和分析
 *   <li>通过识别相关中继尝试来简化故障排查
 * </ul>
 *
 * <p><b>不变性:</b> 一旦创建,批次 ID 不可更改。使用 {@link #generate(Instant)} 生成新 ID, 使用 {@link #of(String)}
 * 从字符串重建 ID(带格式验证)。
 *
 * @author Patra Team
 * @since 0.1.0
 */
public final class RelayBatchId {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

  private static final Pattern VALID_PATTERN = Pattern.compile("\\d{14}-[a-f0-9]{8}");

  private final String value;

  private RelayBatchId(String value) {
    this.value = Objects.requireNonNull(value, "批次 ID 值不能为 null");
  }

  /**
   * 工厂方法: 根据触发时间戳生成新的批次 ID。
   *
   * <p>生成的 ID 通过随机 UUID 组件保证并发调用下的唯一性。
   *
   * @param triggeredAt 中继批次触发时间(UTC)
   * @return 新的 RelayBatchId 实例
   * @throws NullPointerException 如果 triggeredAt 为 null
   */
  public static RelayBatchId generate(Instant triggeredAt) {
    Objects.requireNonNull(triggeredAt, "触发时间不能为 null");
    String timestamp = FORMATTER.format(triggeredAt);
    String uuid = IdUtil.fastSimpleUUID().substring(0, 8);
    return new RelayBatchId(timestamp + "-" + uuid);
  }

  /**
   * 工厂方法: 从字符串值重建 RelayBatchId(带格式验证)。
   *
   * @param value 批次 ID 字符串(格式: yyyyMMddHHmmss-xxxxxxxx)
   * @return RelayBatchId 实例
   * @throws IllegalArgumentException 如果格式无效
   */
  public static RelayBatchId of(String value) {
    if (value == null || !VALID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "无效的批次 ID 格式: " + value + ",期望格式: yyyyMMddHHmmss-xxxxxxxx");
    }
    return new RelayBatchId(value);
  }

  /**
   * 获取批次 ID 的底层字符串值。
   *
   * @return 批次 ID 字符串
   */
  public String getValue() {
    return value;
  }

  /**
   * 提取批次 ID 的时间戳部分。
   *
   * <p>示例: {@code 20251031150000-a1b2c3d4} → {@code 20251031150000}
   *
   * @return 时间戳字符串(yyyyMMddHHmmss 格式)
   */
  public String getTimestampPart() {
    return value.substring(0, 14);
  }

  /**
   * 提取批次 ID 的 UUID 部分。
   *
   * <p>示例: {@code 20251031150000-a1b2c3d4} → {@code a1b2c3d4}
   *
   * @return UUID 字符串(8 位十六进制)
   */
  public String getUuidPart() {
    return value.substring(15);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RelayBatchId that)) {
      return false;
    }
    return value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return value;
  }
}
