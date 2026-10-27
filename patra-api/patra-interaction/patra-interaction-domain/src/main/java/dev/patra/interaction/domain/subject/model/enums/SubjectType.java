package dev.patra.interaction.domain.subject.model.enums;

import dev.patra.interaction.domain.subject.exception.InvalidSubjectKeyException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// 可被互动的对象种类与它的能力集（能力矩阵）。
///
/// `code` 落库、`slug` 进 `(type, key)` 与 URL，两者都永不复用、永不改。新增一种对象：加一个常量；外部对象再加
/// 一个 `SubjectSourcePort` 实现；一条改 `ck_registry_target_type` 与格式约束的迁移，本地对象还要改
/// `ck_registry_owner_local`。
public enum SubjectType {
  /// 文献：key 是 catalog 内部 ID，身份锚是 PMID；可以收藏、讨论。
  PUBLICATION(
      (short) 1,
      "publication",
      "文献",
      SubjectOrigin.EXTERNAL,
      NaturalKeyScheme.PMID,
      EnumSet.of(SubjectCapability.COLLECT, SubjectCapability.DISCUSS)),

  /// 期刊：key 是 catalog 期刊内部 ID，身份锚是 NLM ID；可以挂投稿经验。
  JOURNAL(
      (short) 2,
      "journal",
      "期刊",
      SubjectOrigin.EXTERNAL,
      NaturalKeyScheme.NLM,
      EnumSet.of(SubjectCapability.SUBMISSION_EXPERIENCE)),

  /// 投稿经验（按帖子结构存）：本地对象，key 是帖子 ID，身份锚是 `post:{帖子 ID}`；可以讨论。以后的帖子类型共用它。
  POST(
      (short) 3,
      "post",
      "投稿经验",
      SubjectOrigin.LOCAL,
      NaturalKeyScheme.POST,
      EnumSet.of(SubjectCapability.DISCUSS));

  /// 规范的十进制正整数（不含符号、前导零、空白），位数上限为 `long` 的 19 位。
  private static final Pattern SOURCE_ID = Pattern.compile("[1-9][0-9]{0,18}");

  private final short code;
  private final String slug;
  private final String displayName;
  private final SubjectOrigin origin;
  private final NaturalKeyScheme naturalKeyScheme;
  private final Set<SubjectCapability> capabilities;

  /// 定义一种对象。
  ///
  /// @param code 落库编码
  /// @param slug 对外类型名
  /// @param displayName 消息里的名词
  /// @param origin 外部对象或本地对象
  /// @param naturalKeyScheme 身份锚种类
  /// @param capabilities 能力集
  SubjectType(
      short code,
      String slug,
      String displayName,
      SubjectOrigin origin,
      NaturalKeyScheme naturalKeyScheme,
      EnumSet<SubjectCapability> capabilities) {
    this.code = code;
    this.slug = slug;
    this.displayName = displayName;
    this.origin = origin;
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

  /// 消息里的名词，如「文献」。
  ///
  /// @return 名词
  public String displayName() {
    return displayName;
  }

  /// 外部对象或本地对象。
  ///
  /// @return 来源属性
  public SubjectOrigin origin() {
    return origin;
  }

  /// 是否本地对象。
  ///
  /// @return 本地对象时为 `true`
  public boolean isLocal() {
    return origin == SubjectOrigin.LOCAL;
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

  /// 全部外部类型，按声明顺序：启动校验与每日对账只管它们。
  ///
  /// @return 外部类型
  public static List<SubjectType> externalTypes() {
    return Arrays.stream(values()).filter(t -> !t.isLocal()).toList();
  }

  /// 按对外类型名查找。
  ///
  /// @param slug 类型名
  /// @return 对象种类；未知时为空
  public static Optional<SubjectType> fromSlug(String slug) {
    return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst();
  }
}
