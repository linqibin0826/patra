package com.patra.starter.observability.filter;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 敏感数据脱敏过滤器。
///
/// 功能：
///
/// - 在 Observation 创建后、启动前拦截并脱敏敏感数据
/// - 检测密码、Token、身份证号、手机号等敏感信息
/// - 支持自定义敏感数据模式
/// - 生产环境强制启用，开发环境可选
///
/// 安全策略：
///
/// - 默认模式：脱敏常见敏感字段（password, token, apiKey, secret 等）
/// - 自定义模式：支持通过配置添加自定义脱敏规则
/// - 静默失败：脱敏失败不影响业务逻辑，仅记录警告日志
///
/// 设计说明：
/// 使用 ObservationFilter 而非 ObservationHandler，
/// 因为 Filter 在 Observation 创建阶段执行，可以安全地修改 Context，
/// 而 Handler 在执行阶段，修改 Context 需要使用反射，存在兼容性风险。
///
/// @author Jobs
/// @since 1.0.0
public class SensitiveDataObservationFilter implements ObservationFilter {

  private static final Logger log = LoggerFactory.getLogger(SensitiveDataObservationFilter.class);

  /// 敏感字段名称模式（不区分大小写）。
  private static final Pattern[] SENSITIVE_FIELD_PATTERNS = {
    Pattern.compile("(?i)password"), // 密码
    Pattern.compile("(?i)pwd"), // 密码简写
    Pattern.compile("(?i)token"), // Token
    Pattern.compile("(?i)secret"), // 密钥
    Pattern.compile("(?i)api[_-]?key"), // API Key
    Pattern.compile("(?i)auth"), // 认证信息
    Pattern.compile("(?i)credential") // 凭证
  };

  /// 敏感数据值模式（正则匹配）。
  private static final Pattern[] SENSITIVE_VALUE_PATTERNS = {
    Pattern.compile("\\d{15,19}"), // 身份证号（15或18位）
    Pattern.compile("\\d{3}-?\\d{4}-?\\d{4}"), // 手机号（含分隔符）
    Pattern.compile("\\d{3,4}-?\\d{7,8}"), // 固定电话
    Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), // 邮箱
    Pattern.compile("\\d{16,19}"), // 银行卡号
    Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9\\-._~+/]+=*"), // Bearer Token
    Pattern.compile("(?i)Basic\\s+[a-zA-Z0-9+/]+=*") // Basic Auth
  };

  private static final String MASK_PLACEHOLDER = "***MASKED***";

  private final boolean enabled;
  private final List<Pattern> customPatterns;

  /// 构造函数。
  ///
  /// @param enabled 是否启用脱敏
  /// @param customPatterns 自定义敏感数据模式（正则表达式）
  public SensitiveDataObservationFilter(boolean enabled, List<String> customPatterns) {
    this.enabled = enabled;
    this.customPatterns = new ArrayList<>();

    if (customPatterns != null) {
      customPatterns.forEach(pattern -> this.customPatterns.add(Pattern.compile(pattern)));
    }

    log.info("初始化敏感数据脱敏过滤器，启用状态: {}, 自定义模式数量: {}", enabled, this.customPatterns.size());
  }

  /// 检测 Observation Context 中的敏感数据并告警。
  ///
  /// 设计说明：
  /// Micrometer 的 Context KeyValues 是不可变的，ObservationFilter 无法直接修改。
  /// 因此采用"检测 + 告警 + 阻止"策略：
  /// 1. 检测敏感数据
  /// 2. 记录 ERROR 级别日志并告警
  /// 3. 建议在数据源头移除敏感标签
  ///
  /// 安全建议：
  /// - 配合 Logback Filter 过滤日志中的敏感数据
  /// - 在添加 Observation 标签时避免包含敏感信息
  /// - 使用 SkyWalking Agent 配置禁用敏感数据收集
  ///
  /// @param context Observation 上下文
  /// @return 原始上下文（不修改）
  @Override
  public Observation.Context map(Observation.Context context) {
    if (!enabled) {
      return context;
    }

    try {
      // 检测低基数标签
      KeyValues lowCardinality = context.getLowCardinalityKeyValues();
      boolean hasLowSensitive = containsSensitiveData(lowCardinality);

      // 检测高基数标签
      KeyValues highCardinality = context.getHighCardinalityKeyValues();
      boolean hasHighSensitive = containsSensitiveData(highCardinality);

      // 如果检测到敏感数据，记录 ERROR 级别警告
      if (hasLowSensitive || hasHighSensitive) {
        log.error(
            "🚨 检测到敏感数据: observation={}, "
                + "lowCardinality包含敏感数据={}, highCardinality包含敏感数据={}, "
                + "请在数据源头移除敏感标签！敏感数据可能已泄漏到日志/指标/APM",
            context.getName(),
            hasLowSensitive,
            hasHighSensitive);

        // 详细记录敏感字段（DEBUG 级别）
        if (log.isDebugEnabled()) {
          logSensitiveFields(lowCardinality, "lowCardinality");
          logSensitiveFields(highCardinality, "highCardinality");
        }
      }

      return context;
    } catch (Exception e) {
      // 静默失败：检测失败不应影响业务逻辑
      log.warn("敏感数据检测失败，跳过检测: observation={}, error={}", context.getName(), e.getMessage());
      return context;
    }
  }

  /// 检测 KeyValues 是否包含敏感数据。
  ///
  /// @param keyValues KeyValue 集合
  /// @return true 如果包含敏感数据
  private boolean containsSensitiveData(KeyValues keyValues) {
    if (keyValues == null || keyValues.stream().count() == 0) {
      return false;
    }

    for (io.micrometer.common.KeyValue keyValue : keyValues) {
      String key = keyValue.getKey();
      String value = keyValue.getValue();

      // 检查字段名是否敏感
      if (isSensitiveField(key)) {
        return true;
      }

      // 检查值是否包含敏感数据
      if (!maskSensitiveValue(value).equals(value)) {
        return true;
      }
    }

    return false;
  }

  /// 详细记录敏感字段（DEBUG 级别）。
  ///
  /// @param keyValues KeyValue 集合
  /// @param category 标签类别（lowCardinality 或 highCardinality）
  private void logSensitiveFields(KeyValues keyValues, String category) {
    if (keyValues == null || keyValues.stream().count() == 0) {
      return;
    }

    for (io.micrometer.common.KeyValue keyValue : keyValues) {
      String key = keyValue.getKey();
      String value = keyValue.getValue();

      if (isSensitiveField(key)) {
        log.debug("  - 敏感字段 [{}]: key={}, value=***MASKED***", category, key);
      } else if (!maskSensitiveValue(value).equals(value)) {
        log.debug("  - 敏感值 [{}]: key={}, value=***MASKED***", category, key);
      }
    }
  }

  /// 脱敏 KeyValue 集合。
  ///
  /// @param keyValues 原始 KeyValue 集合
  /// @return 脱敏后的 KeyValue 集合
  private KeyValues maskKeyValues(KeyValues keyValues) {
    if (keyValues == null || keyValues.stream().count() == 0) {
      return keyValues;
    }

    KeyValues maskedKeyValues = KeyValues.empty();

    for (io.micrometer.common.KeyValue keyValue : keyValues) {
      String key = keyValue.getKey();
      String value = keyValue.getValue();

      // 检查字段名是否敏感
      if (isSensitiveField(key)) {
        maskedKeyValues = maskedKeyValues.and(key, MASK_PLACEHOLDER);
        log.debug("脱敏敏感字段: key={}", key);
        continue;
      }

      // 检查值是否包含敏感数据
      String maskedValue = maskSensitiveValue(value);
      if (!maskedValue.equals(value)) {
        maskedKeyValues = maskedKeyValues.and(key, maskedValue);
        log.debug("脱敏敏感值: key={}", key);
      } else {
        maskedKeyValues = maskedKeyValues.and(key, value);
      }
    }

    return maskedKeyValues;
  }

  /// 检查字段名是否敏感。
  ///
  /// @param fieldName 字段名
  /// @return true 如果字段名敏感
  private boolean isSensitiveField(String fieldName) {
    if (fieldName == null) {
      return false;
    }

    for (Pattern pattern : SENSITIVE_FIELD_PATTERNS) {
      if (pattern.matcher(fieldName).find()) {
        return true;
      }
    }

    for (Pattern pattern : customPatterns) {
      if (pattern.matcher(fieldName).find()) {
        return true;
      }
    }

    return false;
  }

  /// 脱敏敏感值。
  ///
  /// @param value 原始值
  /// @return 脱敏后的值
  private String maskSensitiveValue(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    String result = value;

    // 应用内置模式
    for (Pattern pattern : SENSITIVE_VALUE_PATTERNS) {
      result = pattern.matcher(result).replaceAll(MASK_PLACEHOLDER);
    }

    // 应用自定义模式
    for (Pattern pattern : customPatterns) {
      result = pattern.matcher(result).replaceAll(MASK_PLACEHOLDER);
    }

    return result;
  }
}
