package dev.patra.interaction.domain.subject.model.enums;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// 可被互动的对象种类与它的能力集（能力矩阵）。
///
/// `code` 落库、`slug` 进 `(type, key)` 与 URL，两者都永不复用、永不改。新增一种对象：加一个常量、
/// 一个 `SubjectSourcePort` 实现、一条改 `ck_registry_target_type` 的迁移。
public enum SubjectType {
  /// 文献：key 是 catalog 内部 ID，身份锚是 PMID。
  PUBLICATION(
      (short) 1, "publication", NaturalKeyScheme.PMID, EnumSet.of(SubjectCapability.COLLECT));

  /// 规范的十进制正整数（不含符号、前导零、空白），位数上限为 `long` 的 19 位。
  private static final Pattern SOURCE_ID = Pattern.compile("[1-9][0-9]{0,18}");

  private final short code;
  private final String slug;
  private final NaturalKeyScheme naturalKeyScheme;
  private final Set<SubjectCapability> capabilities;

  /// 定义一种对象。
  ///
  /// @param code 落库编码
  /// @param slug 对外类型名
  /// @param naturalKeyScheme 身份锚种类
  /// @param capabilities 能力集
  SubjectType(
      short code,
      String slug,
      NaturalKeyScheme naturalKeyScheme,
      EnumSet<SubjectCapability> capabilities) {
    this.code = code;
    this.slug = slug;
    this.naturalKeyScheme = naturalKeyScheme;
    this.capabilities = Set.copyOf(capabilities);
  }

  /// 落库编码。
  ///
  /// @return 编码
  public short code() {
    return code;
  }

  /// 对外类型名。
  ///
  /// @return slug
  public String slug() {
    return slug;
  }

  /// 身份锚种类。
  ///
  /// @return 种类
  public NaturalKeyScheme naturalKeyScheme() {
    return naturalKeyScheme;
  }

  /// 是否支持某种互动。
  ///
  /// @param capability 互动
  /// @return 支持时为 `true`
  public boolean supports(SubjectCapability capability) {
    return capabilities.contains(capability);
  }

  /// 把对外的 key 解析成来源 ID。
  ///
  /// @param key 对外 key（文献是 catalog 内部 ID 的十进制）
  /// @return 来源 ID
  /// @throws InvalidSubjectKeyException key 不是规范的十进制正整数
  public long parseKey(String key) {
    if (key == null || !SOURCE_ID.matcher(key).matches()) {
      throw new InvalidSubjectKeyException(this, key);
    }
    try {
      return Long.parseLong(key);
    } catch (NumberFormatException ex) {
      throw new InvalidSubjectKeyException(this, key);
    }
  }

  /// 按落库编码查找。
  ///
  /// @param code 编码
  /// @return 对象种类
  /// @throws IllegalArgumentException 未知编码（库里出现它是程序错误）
  public static SubjectType fromCode(short code) {
    return Arrays.stream(values())
        .filter(t -> t.code == code)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知的对象类型编码：" + code));
  }

  /// 按对外类型名查找。
  ///
  /// @param slug 类型名
  /// @return 对象种类；未知时为空
  public static Optional<SubjectType> fromSlug(String slug) {
    return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst();
  }
}
