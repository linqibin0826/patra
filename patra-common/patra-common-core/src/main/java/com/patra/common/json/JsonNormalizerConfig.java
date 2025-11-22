package com.patra.common.json;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// 管理 JSON 规范化行为的配置。
///
/// 提供可调选项,包括空值移除、类型强制策略、默认时区、序列字段保留、数组去重、字符串清理、键排序以及安全防护(深度、字符串字节长度、禁止键)。
///
/// 使用 {@link #builder()} 构造具有自定义设置的实例。
public final class JsonNormalizerConfig {
  final boolean removeEmpty;
  final Set<String> keepEmptyWhitelist;
  final CoerceBoolean coerceBoolean;
  final boolean coerceNumber;
  final boolean coerceTime;
  final ZoneId defaultZoneId;
  final Set<String> sequenceFieldWhitelist;
  final boolean arrayDeduplicate;
  final boolean trimStrings;
  final boolean collapseSpaces;
  final Set<String> lowercaseFields;
  final Comparator<String> keyComparator;
  final int maxDepth;
  final int maxStringBytes;
  final Set<String> forbidKeys;

  private JsonNormalizerConfig(Builder builder) {
    this.removeEmpty = builder.removeEmpty;
    this.keepEmptyWhitelist =
        Collections.unmodifiableSet(new LinkedHashSet<>(builder.keepEmptyWhitelist));
    this.coerceBoolean = builder.coerceBoolean;
    this.coerceNumber = builder.coerceNumber;
    this.coerceTime = builder.coerceTime;
    this.defaultZoneId = builder.defaultZoneId;
    this.sequenceFieldWhitelist =
        Collections.unmodifiableSet(new LinkedHashSet<>(builder.sequenceFieldWhitelist));
    this.arrayDeduplicate = builder.arrayDeduplicate;
    this.trimStrings = builder.trimStrings;
    this.collapseSpaces = builder.collapseSpaces;
    this.lowercaseFields =
        Collections.unmodifiableSet(new LinkedHashSet<>(builder.lowercaseFields));
    this.keyComparator = builder.sortComparator.comparator;
    this.maxDepth = builder.maxDepth;
    this.maxStringBytes = builder.maxStringBytes;
    this.forbidKeys = Collections.unmodifiableSet(new HashSet<>(builder.forbidKeys));
  }

  /// 创建用于构造配置实例的新构建器。
  public static Builder builder() {
    return new Builder();
  }

  /// 字符串到布尔值转换的布尔强制策略。
  public enum CoerceBoolean {
    /// 不强制;布尔值保持解析状态。
    NONE,

    /// 严格强制:仅 "true" 和 "false" 字符串。
    STRICT,

    /// 宽松强制:"true"/"1"/"yes" 转为 true,"false"/"0"/"no" 转为 false。
    LOOSE
  }

  /// 用于排序 JSON 对象键的比较器策略。
  public enum SortComparator {
    /// ASCII 自然顺序(逐字节比较)。
    ASCII(Comparator.naturalOrder()),

    /// 使用默认语言环境的 Unicode 排序顺序。
    UNICODE(java.text.Collator.getInstance(Locale.ROOT)::compare);

    private final Comparator<String> comparator;

    SortComparator(Comparator<String> comparator) {
      this.comparator = comparator;
    }
  }

  /// {@link JsonNormalizerConfig} 的构建器。
  ///
  /// 默认值倾向于稳定但宽松的规范化并减少噪声;根据需要覆盖选项以实现更严格或更宽松的行为。
  public static final class Builder {
    private boolean removeEmpty = true;
    private final Set<String> keepEmptyWhitelist = new LinkedHashSet<>();
    private CoerceBoolean coerceBoolean = CoerceBoolean.NONE;
    private boolean coerceNumber = true;
    private boolean coerceTime = false;
    private ZoneId defaultZoneId = ZoneOffset.UTC;
    private final Set<String> sequenceFieldWhitelist = new LinkedHashSet<>();
    private boolean arrayDeduplicate = true;
    private boolean trimStrings = true;
    private boolean collapseSpaces = true;
    private final Set<String> lowercaseFields = new LinkedHashSet<>();
    private SortComparator sortComparator = SortComparator.ASCII;
    private int maxDepth = 64;
    private int maxStringBytes = 64 * 1024;
    private final Set<String> forbidKeys = new HashSet<>();

    /// 设置是否移除空值(null、空字符串、空集合/映射)。
    public Builder removeEmpty(boolean removeEmpty) {
      this.removeEmpty = removeEmpty;
      return this;
    }

    /// 设置即使 removeEmpty 为 true 时也应保留空值的字段。
    public Builder keepEmptyWhitelist(Set<String> fields) {
      this.keepEmptyWhitelist.clear();
      if (fields != null) {
        this.keepEmptyWhitelist.addAll(fields);
      }
      return this;
    }

    /// 设置布尔强制策略。
    public Builder coerceBoolean(CoerceBoolean coerceBoolean) {
      this.coerceBoolean = Objects.requireNonNull(coerceBoolean, "coerceBoolean");
      return this;
    }

    /// 设置是否将字符串数字强制转换为 BigDecimal。
    public Builder coerceNumber(boolean coerceNumber) {
      this.coerceNumber = coerceNumber;
      return this;
    }

    /// 设置是否将时间字符串强制转换为规范 ISO-8601 格式。
    public Builder coerceTime(boolean coerceTime) {
      this.coerceTime = coerceTime;
      return this;
    }

    /// 设置仅日期时间值的默认时区。
    public Builder defaultZoneId(ZoneId defaultZoneId) {
      this.defaultZoneId = Objects.requireNonNull(defaultZoneId, "defaultZoneId");
      return this;
    }

    /// 设置应保留数组元素顺序的字段(不排序)。
    public Builder sequenceFieldWhitelist(Set<String> fields) {
      this.sequenceFieldWhitelist.clear();
      if (fields != null) {
        this.sequenceFieldWhitelist.addAll(fields);
      }
      return this;
    }

    /// 设置是否对数组元素去重。
    public Builder arrayDeduplicate(boolean arrayDeduplicate) {
      this.arrayDeduplicate = arrayDeduplicate;
      return this;
    }

    /// 设置是否修剪字符串的前导和尾随空格。
    public Builder trimStrings(boolean trimStrings) {
      this.trimStrings = trimStrings;
      return this;
    }

    /// 设置是否将多个连续空格折叠为单个空格。
    public Builder collapseSpaces(boolean collapseSpaces) {
      this.collapseSpaces = collapseSpaces;
      return this;
    }

    /// 设置字符串值应转换为小写的字段。
    public Builder lowercaseFields(Set<String> fields) {
      this.lowercaseFields.clear();
      if (fields != null) {
        this.lowercaseFields.addAll(fields);
      }
      return this;
    }

    /// 设置用于排序 JSON 对象键的比较器。
    public Builder sortComparator(SortComparator sortComparator) {
      this.sortComparator = Objects.requireNonNull(sortComparator, "sortComparator");
      return this;
    }

    /// 设置 JSON 结构中允许的最大嵌套深度。
    public Builder maxDepth(int maxDepth) {
      if (maxDepth <= 0) {
        throw new IllegalArgumentException("maxDepth must be > 0");
      }
      this.maxDepth = maxDepth;
      return this;
    }

    /// 设置字符串值的最大字节长度(0 表示禁用)。
    public Builder maxStringBytes(int maxStringBytes) {
      if (maxStringBytes < 0) {
        throw new IllegalArgumentException("maxStringBytes must be >= 0");
      }
      this.maxStringBytes = maxStringBytes;
      return this;
    }

    /// 设置禁止的键,如果遇到这些键将导致规范化失败。
    public Builder forbidKeys(Set<String> keys) {
      this.forbidKeys.clear();
      if (keys != null) {
        this.forbidKeys.addAll(keys);
      }
      return this;
    }

    /// 构建配置实例。
    public JsonNormalizerConfig build() {
      return new JsonNormalizerConfig(this);
    }
  }
}
