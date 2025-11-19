package com.patra.catalog.domain.model.vo.author;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/**
 * ORCID 标识符值对象。封装 ORCID(Open Researcher and Contributor ID)的验证和管理。
 *
 * <p>ORCID 格式说明：
 *
 * <ul>
 *   <li>格式：XXXX-XXXX-XXXX-XXXX(16位数字,每4位用连字符分隔)
 *   <li>示例：0000-0001-2345-6789
 *   <li>最后一位可能是X(校验位)
 *   <li>ORCID 是全球唯一的研究者标识符
 * </ul>
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>不可变性：Record 自动提供
 *   <li>格式验证：严格验证 ORCID 格式
 *   <li>校验位验证：支持 ISO 7064 Mod 11-2 校验算法
 *   <li>URL 支持：提供 ORCID 官方 URL
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 创建 ORCID
 * Orcid orcid = Orcid.of("0000-0001-2345-6789");
 * assert orcid.isValid();
 *
 * // 获取 ORCID URL
 * String url = orcid.toUrl();
 * // https://orcid.org/0000-0001-2345-6789
 *
 * // 校验位验证
 * assert orcid.isChecksumValid();
 * }</pre>
 *
 * @param value ORCID 标识符(格式：XXXX-XXXX-XXXX-XXXX)
 * @author linqibin
 * @since 0.1.0
 */
public record Orcid(String value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** ORCID 格式正则表达式 */
  private static final String ORCID_PATTERN = "\\d{4}-\\d{4}-\\d{4}-\\d{3}[\\dX]";

  /** ORCID 官方 URL 前缀 */
  private static final String ORCID_URL_PREFIX = "https://orcid.org/";

  /**
   * 紧凑构造器：验证 ORCID 的有效性。
   *
   * @throws IllegalArgumentException 如果 ORCID 为空或格式无效
   */
  public Orcid {
    Assert.notBlank(value, "ORCID 不能为空");

    // ORCID 格式验证
    Assert.isTrue(
        value.matches(ORCID_PATTERN),
        "ORCID 格式无效,必须符合 'XXXX-XXXX-XXXX-XXXX' 格式：%s",
        value);

    // 标准化为大写(X校验位)
    value = value.toUpperCase();
  }

  /**
   * 创建 ORCID。
   *
   * @param value ORCID 字符串
   * @return ORCID 值对象
   * @throws IllegalArgumentException 如果 ORCID 格式无效
   */
  public static Orcid of(String value) {
    return new Orcid(value);
  }

  /**
   * 从 ORCID URL 创建 ORCID。
   *
   * @param url ORCID URL(如 https://orcid.org/0000-0001-2345-6789)
   * @return ORCID 值对象
   * @throws IllegalArgumentException 如果 URL 格式无效
   */
  public static Orcid fromUrl(String url) {
    Assert.notBlank(url, "ORCID URL 不能为空");

    String orcidValue;
    if (url.startsWith(ORCID_URL_PREFIX)) {
      orcidValue = url.substring(ORCID_URL_PREFIX.length());
    } else {
      throw new IllegalArgumentException("无效的 ORCID URL：" + url);
    }

    return new Orcid(orcidValue);
  }

  /**
   * 验证 ORCID 格式是否有效。
   *
   * @param value ORCID 字符串
   * @return true 如果格式有效
   */
  public static boolean isValid(String value) {
    try {
      new Orcid(value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 验证 ORCID 校验位(ISO 7064 Mod 11-2 算法)。
   *
   * <p>算法说明：
   *
   * <ol>
   *   <li>移除连字符,得到16位数字
   *   <li>从左到右计算前15位的校验和
   *   <li>计算校验位
   *   <li>比较计算值与第16位
   * </ol>
   *
   * @return true 如果校验位正确
   */
  public boolean isChecksumValid() {
    String digits = value.replace("-", "");
    if (digits.length() != 16) {
      return false;
    }

    int total = 0;
    for (int i = 0; i < 15; i++) {
      int digit = Character.getNumericValue(digits.charAt(i));
      total = (total + digit) * 2;
    }

    int remainder = total % 11;
    int checkDigit = (12 - remainder) % 11;
    char expectedCheck = (checkDigit == 10) ? 'X' : (char) ('0' + checkDigit);

    return digits.charAt(15) == expectedCheck;
  }

  /**
   * 获取 ORCID 官方 URL。
   *
   * @return ORCID URL
   */
  public String toUrl() {
    return ORCID_URL_PREFIX + value;
  }

  /**
   * 获取无连字符的 ORCID。
   *
   * @return 16位数字字符串
   */
  public String toPlainString() {
    return value.replace("-", "");
  }

  @Override
  public String toString() {
    return value;
  }
}
