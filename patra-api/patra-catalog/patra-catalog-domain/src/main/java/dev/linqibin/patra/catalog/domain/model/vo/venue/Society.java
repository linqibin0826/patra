package dev.linqibin.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 关联学会值对象。封装载体关联的学术学会/组织信息。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 简洁性：仅包含 URL 和组织名称
/// - 可追溯：URL 指向学会官方页面
///
/// 使用示例：
///
/// ```java
/// Society society = Society.of(
///     "https://www.acs.org",
///     "American Chemical Society"
/// );
/// ```
///
/// @param url 学会主页 URL
/// @param organization 学会/组织名称
/// @author linqibin
/// @since 0.1.0
public record Society(String url, String organization) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证学会信息的有效性。
  ///
  /// @throws IllegalArgumentException 如果组织名称为空
  public Society {
    Assert.notBlank(organization, "学会/组织名称不能为空");
    // URL 可选，某些学会可能没有官方网站
  }

  /// 创建关联学会。
  ///
  /// @param url 学会主页 URL
  /// @param organization 学会/组织名称
  /// @return 学会值对象
  public static Society of(String url, String organization) {
    return new Society(url, organization);
  }

  /// 创建关联学会（仅组织名称，无 URL）。
  ///
  /// @param organization 学会/组织名称
  /// @return 学会值对象
  public static Society ofOrganization(String organization) {
    return new Society(null, organization);
  }

  /// 判断是否有 URL。
  ///
  /// @return true 如果有 URL
  public boolean hasUrl() {
    return url != null && !url.isBlank();
  }
}
