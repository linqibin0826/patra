package com.patra.catalog.domain.model.vo.affiliation;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// ROR 标识符值对象。封装 ROR(Research Organization Registry)的验证和管理。
/// 
/// **ROR 格式说明**：
/// 
/// - 格式：https://ror.org/0XXXXXX(9位字符)
///   - 示例：https://ror.org/03vek6s52
///   - ROR 是全球研究机构的唯一标识符
///   - 由 Crossref、DataCite 等组织维护
/// 
/// **设计原则**：
/// 
/// - 不可变性：Record 自动提供
///   - 格式验证：严格验证 ROR 格式
///   - URL 支持：提供 ROR 官方 URL
///   - ID 提取：提供纯 ID 提取(不含 URL 前缀)
/// 
/// **使用示例**：
/// 
/// ```java
/// // 从完整 URL 创建 ROR
/// RorId rorId1 = RorId.of("https://ror.org/03vek6s52");
/// assert rorId1.isValid();
/// 
/// // 从纯 ID 创建 ROR
/// RorId rorId2 = RorId.fromId("03vek6s52");
/// assert "https://ror.org/03vek6s52".equals(rorId2.value());
/// 
/// // 获取纯 ID
/// String id = rorId1.getId(); // "03vek6s52"
/// ```
/// 
/// @param value ROR 标识符(格式：https://ror.org/0XXXXXX 或 0XXXXXX)
/// @author linqibin
/// @since 0.1.0
public record RorId(String value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// ROR URL 前缀
  private static final String ROR_URL_PREFIX = "https://ror.org/";

  /// ROR ID 格式正则表达式(9位字符,以0开头)
  private static final String ROR_ID_PATTERN = "0[a-z0-9]{8}";

  /// 紧凑构造器：验证 ROR 的有效性并标准化为完整 URL。
/// 
/// @throws IllegalArgumentException 如果 ROR 为空或格式无效
  public RorId {
    Assert.notBlank(value, "ROR 标识符不能为空");

    // 如果输入是纯 ID,转换为完整 URL
    if (!value.startsWith(ROR_URL_PREFIX)) {
      // 验证纯 ID 格式
      Assert.isTrue(
          value.matches(ROR_ID_PATTERN),
          "ROR ID 格式无效,必须为 9 位字符且以 0 开头：%s",
          value);
      value = ROR_URL_PREFIX + value;
    } else {
      // 验证完整 URL 格式
      String id = value.substring(ROR_URL_PREFIX.length());
      Assert.isTrue(
          id.matches(ROR_ID_PATTERN),
          "ROR URL 格式无效,ID 部分必须为 9 位字符且以 0 开头：%s",
          value);
    }
  }

  /// 从完整 URL 或纯 ID 创建 ROR。
/// 
/// @param value ROR URL 或 ROR ID
/// @return ROR 值对象
/// @throws IllegalArgumentException 如果格式无效
  public static RorId of(String value) {
    return new RorId(value);
  }

  /// 从纯 ID 创建 ROR(不含 URL 前缀)。
/// 
/// @param id ROR ID(9位字符,如 "03vek6s52")
/// @return ROR 值对象(完整 URL)
/// @throws IllegalArgumentException 如果格式无效
  public static RorId fromId(String id) {
    Assert.notBlank(id, "ROR ID 不能为空");
    Assert.isTrue(
        id.matches(ROR_ID_PATTERN), "ROR ID 格式无效,必须为 9 位字符且以 0 开头：%s", id);
    return new RorId(ROR_URL_PREFIX + id);
  }

  /// 验证 ROR 格式是否有效。
/// 
/// @param value ROR URL 或 ROR ID
/// @return true 如果格式有效
  public static boolean isValid(String value) {
    try {
      new RorId(value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /// 获取纯 ID(不含 URL 前缀)。
/// 
/// @return ROR ID(9位字符)
  public String getId() {
    return value.substring(ROR_URL_PREFIX.length());
  }

  /// 获取完整 URL。
/// 
/// @return ROR URL
  public String toUrl() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
