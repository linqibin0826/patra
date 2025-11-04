package com.patra.common.storage;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 包含生成对象存储键所需的所有信息的不可变上下文。
 *
 * <p>此记录封装了所有 Patra 微服务中用于对象键生成的标准段, 确保一致的命名约定和结构。
 *
 * <p><b>标准对象键模式</b>: {@code {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
 *
 * <p><b>示例</b>: {@code ingest/literature-batch/2025/10/26/pubmed-123-batch-001.json}
 *
 * @param serviceName 微服务名称(短格式,例如 "ingest"、"storage"、"catalog")
 * @param businessType 业务类别,使用 kebab-case(例如 "literature-batch"、"metadata-snapshot")
 * @param businessId 此特定业务实体的唯一标识符(例如 "pubmed-123-batch-001")
 * @param partitionDate 用于时间分区的日期(yyyy/MM/dd 结构)
 * @param extension 不带前导点的文件扩展名(例如 "json"、"json.gz"、"xml")
 * @param customSegments 可选的自定义路径段键值对(不可变映射)
 * @author linqibin
 * @since 0.1.0
 */
public record ObjectKeyContext(
    String serviceName,
    String businessType,
    String businessId,
    LocalDate partitionDate,
    String extension,
    Map<String, String> customSegments) {

  /**
   * 带验证和防御性复制的紧凑构造函数。
   *
   * @throws IllegalArgumentException 如果任何必需字段为空或 null
   */
  public ObjectKeyContext {
    validateNonBlank(serviceName, "serviceName");
    validateNonBlank(businessType, "businessType");
    validateNonBlank(businessId, "businessId");
    Objects.requireNonNull(partitionDate, "partitionDate 不能为 null");
    validateNonBlank(extension, "extension");

    // 确保自定义段是不可变的且非 null
    customSegments = sanitizeCustomSegments(customSegments);
  }

  /**
   * 创建仅包含必需字段的上下文(无自定义段)。
   *
   * @param serviceName 微服务名称(短格式)
   * @param businessType 业务类别(kebab-case)
   * @param businessId 唯一业务标识符
   * @param partitionDate 分区日期
   * @param extension 文件扩展名
   * @return 初始化的上下文
   */
  public static ObjectKeyContext of(
      String serviceName,
      String businessType,
      String businessId,
      LocalDate partitionDate,
      String extension) {
    return new ObjectKeyContext(
        serviceName, businessType, businessId, partitionDate, extension, Map.of());
  }

  /**
   * 创建用于流式构建的构建器,支持可选的自定义段。
   *
   * @return 新的构建器实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /** 验证字符串既不为 null 也不为空白。 */
  private static void validateNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " 不能为 null 或空白");
    }
  }

  /** 确保自定义段映射是不可变的且非 null。 */
  private static Map<String, String> sanitizeCustomSegments(Map<String, String> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, String> copy = new LinkedHashMap<>(source.size());
    source.forEach(
        (key, value) -> {
          Objects.requireNonNull(key, "自定义段键不能为 null");
          copy.put(key, value);
        });
    return Collections.unmodifiableMap(copy);
  }

  /** {@link ObjectKeyContext} 的流式 API 构建器。 */
  public static final class Builder {
    private String serviceName;
    private String businessType;
    private String businessId;
    private LocalDate partitionDate;
    private String extension;
    private Map<String, String> customSegments = new LinkedHashMap<>();

    private Builder() {}

    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public Builder businessType(String businessType) {
      this.businessType = businessType;
      return this;
    }

    public Builder businessId(String businessId) {
      this.businessId = businessId;
      return this;
    }

    public Builder partitionDate(LocalDate partitionDate) {
      this.partitionDate = partitionDate;
      return this;
    }

    public Builder extension(String extension) {
      this.extension = extension;
      return this;
    }

    /**
     * 添加自定义段键值对。
     *
     * @param key 段名称
     * @param value 段值
     * @return 此构建器
     */
    public Builder customSegment(String key, String value) {
      this.customSegments.put(key, value);
      return this;
    }

    /**
     * 构建不可变的上下文实例。
     *
     * @return 初始化的 {@link ObjectKeyContext}
     * @throws IllegalArgumentException 如果任何必需字段缺失或无效
     */
    public ObjectKeyContext build() {
      return new ObjectKeyContext(
          serviceName, businessType, businessId, partitionDate, extension, customSegments);
    }
  }
}
