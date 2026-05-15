package dev.linqibin.patra.catalog.domain.model.vo.organization;

import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.linqibin.patra.catalog.domain.model.enums.LinkType;
import java.io.Serial;
import java.io.Serializable;

/// 机构链接值对象（JSON 嵌入）。
///
/// 字段映射：cat_organization.links（JSON 数组）
///
/// 基于 ROR Schema v2.0 的 links 字段定义。作为 JSON 嵌入主表，
/// 不单独建表。
///
/// **ROR Schema 结构**：
///
/// ```json
/// "links": [
///   {"type": "website", "value": "https://www.harvard.edu"},
///   {"type": "wikipedia", "value": "https://en.wikipedia.org/wiki/Harvard_University"}
/// ]
/// ```
///
/// **链接类型说明**：
///
/// | 类型 | 说明 |
/// |------|------|
/// | WEBSITE | 机构官方网站 |
/// | WIKIPEDIA | Wikipedia 页面 |
///
/// **Jackson 注解设计决策**：
///
/// - **@JsonIgnoreProperties(ignoreUnknown = true)**：防御性设计，忽略未知字段
/// - **@JsonIgnore**：标记 `isXxx()` 便捷方法，避免被 Jackson 序列化为冗余的布尔属性
///   （Jackson 默认会把 `isExternal()` 序列化为 `"external": true`，导致反序列化失败）
///
/// @param type 链接类型
/// @param value 链接 URL
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#links">ROR Links Field</a>
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizationLink(LinkType type, String value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public OrganizationLink {
    Assert.notNull(type, "链接类型不能为空");
    Assert.notBlank(value, "链接值不能为空");
  }

  // ========== 工厂方法 ==========

  /// 创建链接。
  ///
  /// @param type 链接类型
  /// @param value 链接 URL
  /// @return 链接值对象
  public static OrganizationLink of(LinkType type, String value) {
    return new OrganizationLink(type, value);
  }

  /// 创建官方网站链接。
  ///
  /// @param url 网站 URL
  /// @return 链接值对象
  public static OrganizationLink website(String url) {
    return new OrganizationLink(LinkType.WEBSITE, url);
  }

  /// 创建 Wikipedia 链接。
  ///
  /// @param url Wikipedia URL
  /// @return 链接值对象
  public static OrganizationLink wikipedia(String url) {
    return new OrganizationLink(LinkType.WIKIPEDIA, url);
  }

  // ========== 查询方法 ==========

  /// 判断是否为官方网站链接。
  ///
  /// @return true 如果是 WEBSITE
  @JsonIgnore
  public boolean isWebsite() {
    return type == LinkType.WEBSITE;
  }

  /// 判断是否为 Wikipedia 链接。
  ///
  /// @return true 如果是 WIKIPEDIA
  @JsonIgnore
  public boolean isWikipedia() {
    return type == LinkType.WIKIPEDIA;
  }

  /// 判断是否为外部参考链接（非机构自身的链接）。
  ///
  /// @return true 如果是外部链接
  @JsonIgnore
  public boolean isExternal() {
    return type.isExternal();
  }

  @Override
  public String toString() {
    return String.format("OrganizationLink[type=%s, value=%s]", type, value);
  }
}
