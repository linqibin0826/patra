package dev.linqibin.patra.catalog.domain.model.vo.author;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// 作者名字变体值对象。
///
/// 解析自 PubMed Computed Authors 的 names 数组，格式如 "Lu,Zhiyong,Z"。
///
/// PubMed names 数组格式说明：
///
/// - **完整格式**：`姓,名,缩写`（如 "Lu,Zhiyong,Z"）
/// - **两部分格式**：`姓,缩写`（如 "Smith,JK"）
/// - **单部分格式**：`姓`（如 "Einstein"）
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 原始字符串保留：fullString 保存原始输入，便于调试和溯源
/// - 标准化支持：提供 toNormalizedForm() 用于去重和比较
///
/// 使用示例：
///
/// ```java
/// // 从 PubMed 格式解析
/// AuthorNameVariant variant = AuthorNameVariant.parse("Lu,Zhiyong,Z");
/// assert "Lu".equals(variant.lastName());
/// assert "Zhiyong".equals(variant.foreName());
/// assert "Z".equals(variant.initials());
///
/// // 获取显示名称
/// String display = variant.toDisplayString();
/// // "Zhiyong Lu"
///
/// // 获取标准化形式（用于去重）
/// String normalized = variant.toNormalizedForm();
/// // "lu+z"
/// ```
///
/// @param lastName 姓（Last Name/Family Name）
/// @param foreName 名（First Name/Given Name，可选）
/// @param initials 姓名缩写（如 "Z", "JK"）
/// @param fullString 原始字符串（如 "Lu,Zhiyong,Z"），必填
/// @author linqibin
/// @since 0.1.0
public record AuthorNameVariant(
    String lastName, String foreName, String initials, String fullString) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 字段最大长度限制（与数据库 VARCHAR 长度对齐）。
  private static final int MAX_LAST_NAME_LENGTH = 200;
  private static final int MAX_FORE_NAME_LENGTH = 200;
  private static final int MAX_INITIALS_LENGTH = 10;
  private static final int MAX_FULL_STRING_LENGTH = 300;

  /// 紧凑构造器：验证必填字段并截断超长字段。
  ///
  /// 超长字段会被静默截断到最大长度，确保数据库存储安全。
  ///
  /// @throws IllegalArgumentException 如果 fullString 为空
  public AuthorNameVariant {
    Assert.notBlank(fullString, "原始字符串不能为空");

    // 截断超长字段（防止数据库 Data truncation 错误）
    lastName = truncate(lastName, MAX_LAST_NAME_LENGTH);
    foreName = truncate(foreName, MAX_FORE_NAME_LENGTH);
    initials = truncate(initials, MAX_INITIALS_LENGTH);
    fullString = truncate(fullString, MAX_FULL_STRING_LENGTH);
  }

  /// 截断字符串到指定长度。
  ///
  /// @param value 原始字符串
  /// @param maxLength 最大长度
  /// @return 截断后的字符串，如果原始值为 null 则返回 null
  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }

  /// 从 PubMed names 数组格式解析名字变体。
  ///
  /// 支持的格式：
  /// - `"Lu,Zhiyong,Z"` → lastName=Lu, foreName=Zhiyong, initials=Z
  /// - `"Smith,JK"` → lastName=Smith, foreName=null, initials=JK（第二部分 ≤ 10 字符视为缩写）
  /// - `"Smith,John Kennedy"` → lastName=Smith, foreName=John Kennedy, initials=null（第二部分 > 10
  // 字符视为名字）
  /// - `"Einstein"` → lastName=Einstein, foreName=null, initials=null
  ///
  /// **智能解析规则**：
  ///
  /// 两部分格式时，根据第二部分长度判断：
  /// - ≤ 10 字符：视为 initials（缩写通常 1-5 字符）
  /// - > 10 字符：视为 foreName（可能是数据格式异常）
  ///
  /// @param rawString 原始字符串
  /// @return 名字变体值对象
  /// @throws IllegalArgumentException 如果原始字符串为空
  public static AuthorNameVariant parse(String rawString) {
    Assert.notBlank(rawString, "原始字符串不能为空");

    String[] parts = rawString.split(",");
    String lastName = parts[0].trim();
    String foreName = null;
    String initials = null;

    if (parts.length == 3) {
      // 完整格式：姓,名,缩写
      foreName = parts[1].trim();
      initials = parts[2].trim();
    } else if (parts.length == 2) {
      // 两部分格式：智能判断第二部分是缩写还是名字
      String secondPart = parts[1].trim();
      if (secondPart.length() <= MAX_INITIALS_LENGTH) {
        // 短字符串视为缩写
        initials = secondPart;
      } else {
        // 长字符串视为名字（数据格式异常的容错处理）
        foreName = secondPart;
      }
    }
    // 单部分格式：仅姓

    return new AuthorNameVariant(lastName, foreName, initials, rawString);
  }

  /// 创建名字变体。
  ///
  /// @param lastName 姓
  /// @param foreName 名（可选）
  /// @param initials 缩写（可选）
  /// @param fullString 原始字符串
  /// @return 名字变体值对象
  public static AuthorNameVariant of(
      String lastName, String foreName, String initials, String fullString) {
    return new AuthorNameVariant(lastName, foreName, initials, fullString);
  }

  /// 判断是否有姓氏。
  ///
  /// @return true 如果有姓氏
  public boolean hasLastName() {
    return StrUtil.isNotBlank(lastName);
  }

  /// 判断是否有名字。
  ///
  /// @return true 如果有名字
  public boolean hasForeName() {
    return StrUtil.isNotBlank(foreName);
  }

  /// 判断是否有缩写。
  ///
  /// @return true 如果有缩写
  public boolean hasInitials() {
    return StrUtil.isNotBlank(initials);
  }

  /// 获取显示字符串。
  ///
  /// 规则：
  /// - 如果有名字：返回 "名 姓"（如 "Zhiyong Lu"）
  /// - 如果仅有姓和缩写：返回 "姓, 缩写"（如 "Smith, JK"）
  /// - 如果仅有姓：返回姓（如 "Einstein"）
  ///
  /// @return 格式化的显示名称
  public String toDisplayString() {
    if (hasForeName()) {
      // 有名字：名 姓
      return foreName + " " + lastName;
    } else if (hasInitials()) {
      // 仅姓和缩写：姓, 缩写
      return lastName + ", " + initials;
    } else {
      // 仅姓
      return lastName;
    }
  }

  /// 获取标准化形式（用于去重和业务键生成）。
  ///
  /// 规则：
  /// - 格式：姓+缩写（如 "lu+z"）
  /// - 转小写
  /// - 移除空格
  /// - 无缩写时仅返回姓
  ///
  /// @return 标准化的名字形式
  public String toNormalizedForm() {
    StringBuilder sb = new StringBuilder();

    if (hasLastName()) {
      // 移除空格并转小写
      sb.append(lastName.replaceAll("\\s+", "").toLowerCase());
    }

    if (hasInitials()) {
      sb.append("+");
      sb.append(initials.toLowerCase());
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return fullString;
  }
}
