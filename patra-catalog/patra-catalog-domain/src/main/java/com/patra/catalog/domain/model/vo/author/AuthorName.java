package com.patra.catalog.domain.model.vo.author;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// 作者姓名值对象。封装作者的姓名结构(姓+名+缩写+后缀)。
/// 
/// 设计原则：
/// 
/// - 不可变性：Record 自动提供
///   - 完整性：支持西方姓名结构(Last Name + First Name + Initials + Suffix)
///   - 灵活性：所有字段可选(支持不完整姓名)
///   - 去重支持：提供标准化姓名用于去重
/// 
/// 姓名结构说明：
/// 
/// - **Last Name**：姓氏,如 "Smith", "李"
///   - **Fore Name**：名字,如 "John", "明"
///   - **Initials**：姓名缩写,如 "J.K.", "M."
///   - **Suffix**：后缀,如 "Jr.", "III", "PhD", "MD"
/// 
/// 使用示例：
/// 
/// ```java
/// // 完整姓名
/// AuthorName fullName = AuthorName.of("Smith", "John", "J.", "Jr.");
/// assert "Smith, J. Jr.".equals(fullName.toShortForm());
/// 
/// // 仅姓氏和缩写(PubMed常见格式)
/// AuthorName shortName = AuthorName.of("Smith", null, "J.", null);
/// 
/// // 中文姓名
/// AuthorName chineseName = AuthorName.of("李", "明", null, null);
/// assert "李明".equals(chineseName.toFullForm());
/// ```
/// 
/// @param lastName 姓氏(Last Name/Family Name)
/// @param foreName 名字(First Name/Given Name)
/// @param initials 姓名缩写(如 "J.K.")
/// @param suffix 后缀(如 "Jr.", "PhD")
/// @author linqibin
/// @since 0.1.0
public record AuthorName(
    String lastName,
    String foreName,
    String initials,
    String suffix)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证姓名的有效性。
/// 
/// @throws IllegalArgumentException 如果所有字段都为空
  public AuthorName {
    // 至少需要提供一个字段
    Assert.isTrue(
        StrUtil.isNotBlank(lastName)
            || StrUtil.isNotBlank(foreName)
            || StrUtil.isNotBlank(initials),
        "姓名至少需要提供姓氏、名字或缩写之一");
  }

  /// 创建作者姓名。
/// 
/// @param lastName 姓氏
/// @param foreName 名字
/// @param initials 缩写
/// @param suffix 后缀
/// @return 作者姓名值对象
  public static AuthorName of(String lastName, String foreName, String initials, String suffix) {
    return new AuthorName(lastName, foreName, initials, suffix);
  }

  /// 创建仅包含姓氏和名字的姓名。
/// 
/// @param lastName 姓氏
/// @param foreName 名字
/// @return 作者姓名值对象
  public static AuthorName of(String lastName, String foreName) {
    return new AuthorName(lastName, foreName, null, null);
  }

  /// 创建仅包含姓氏和缩写的姓名(PubMed常见格式)。
/// 
/// @param lastName 姓氏
/// @param initials 缩写
/// @return 作者姓名值对象
  public static AuthorName withInitials(String lastName, String initials) {
    return new AuthorName(lastName, null, initials, null);
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

  /// 判断是否有后缀。
/// 
/// @return true 如果有后缀
  public boolean hasSuffix() {
    return StrUtil.isNotBlank(suffix);
  }

  /// 获取完整姓名(西方格式)。
/// 
/// 格式：First Name Last Name, Suffix
/// 
/// 示例："John Smith, Jr."
/// 
/// @return 完整姓名
  public String toFullForm() {
    StringBuilder sb = new StringBuilder();

    // 名在前(西方格式)
    if (hasForeName()) {
      sb.append(foreName);
    }

    // 姓在后
    if (hasLastName()) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(lastName);
    }

    // 后缀
    if (hasSuffix()) {
      sb.append(", ").append(suffix);
    }

    return sb.toString();
  }

  /// 获取简短格式姓名(学术引用格式)。
/// 
/// 格式：Last Name, Initials Suffix
/// 
/// 示例："Smith, J. Jr." 或 "Smith, J.K."
/// 
/// @return 简短格式姓名
  public String toShortForm() {
    StringBuilder sb = new StringBuilder();

    // 姓氏在前
    if (hasLastName()) {
      sb.append(lastName);
    }

    // 缩写在后
    if (hasInitials()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(initials);
    }

    // 后缀
    if (hasSuffix()) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(suffix);
    }

    return sb.toString();
  }

  /// 获取标准化姓名(用于去重)。
/// 
/// 规则：
/// 
/// - 转小写
///   - 移除所有空格
///   - 移除标点符号(点号、逗号等)
///   - 优先使用姓氏+缩写
/// 
/// @return 标准化姓名
  public String toNormalizedForm() {
    StringBuilder sb = new StringBuilder();

    if (hasLastName()) {
      sb.append(lastName);
    }

    if (hasInitials()) {
      sb.append(initials);
    } else if (hasForeName()) {
      sb.append(foreName);
    }

    return sb.toString()
        .toLowerCase()
        .replaceAll("[\\s.,;-]", ""); // 移除空格和标点
  }

  /// 获取显示文本。
/// 
/// @return 格式化的姓名
  public String toDisplayString() {
    if (hasForeName()) {
      return toFullForm();
    } else {
      return toShortForm();
    }
  }
}
