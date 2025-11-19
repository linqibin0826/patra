package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.IssnType;
import java.io.Serial;
import java.io.Serializable;

/**
 * ISSN信息值对象。封装期刊的ISSN标准号及其类型。
 *
 * <p>设计原则：
 *
 * <ul>
 *   <li>不可变性：Record 自动提供
 *   <li>格式验证：ISSN必须符合 "XXXX-XXXX" 格式
 *   <li>类型安全：通过枚举确保类型正确性
 *   <li>关联管理：通过 linking ISSN 关联纸质版和电子版
 * </ul>
 *
 * <p>ISSN 格式说明：
 *
 * <ul>
 *   <li>国际标准连续出版物号（ISSN），格式为 "XXXX-XXXX"（8位数字，中间带连字符）
 *   <li>示例：Print ISSN: "1234-5678", Electronic ISSN: "1234-567X"
 *   <li>Linking ISSN: 用于关联同一期刊的不同介质版本
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 创建纸质版ISSN
 * IssnInfo printIssn = IssnInfo.of("1234-5678", IssnType.PRINT, null);
 *
 * // 创建电子版ISSN并关联纸质版
 * IssnInfo eIssn = IssnInfo.of("1234-567X", IssnType.ELECTRONIC, "1234-5678");
 *
 * // 验证ISSN
 * if (printIssn.isPrint()) {
 *     System.out.println("这是纸质版ISSN");
 * }
 * }</pre>
 *
 * @param issn ISSN号码（格式：XXXX-XXXX，必填）
 * @param issnType ISSN类型（print/electronic，必填）
 * @param issnLinking Linking ISSN（关联纸质版和电子版，可选）
 * @author linqibin
 * @since 0.1.0
 */
public record IssnInfo(
    String issn, IssnType issnType, String issnLinking) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** ISSN格式正则表达式：XXXX-XXXX（4位数字-4位数字，最后一位可能是X） */
  private static final String ISSN_PATTERN = "\\d{4}-\\d{3}[\\dXx]";

  /**
   * 紧凑构造器：验证ISSN的有效性。
   *
   * @throws IllegalArgumentException 如果ISSN或类型为空，或格式无效
   */
  public IssnInfo {
    Assert.notBlank(issn, "ISSN不能为空");
    Assert.notNull(issnType, "ISSN类型不能为空");

    // ISSN格式验证
    Assert.isTrue(
        issn.matches(ISSN_PATTERN),
        "ISSN格式无效，必须符合 'XXXX-XXXX' 格式：%s", issn);

    // Linking ISSN格式验证（如果提供）
    if (StrUtil.isNotBlank(issnLinking)) {
      Assert.isTrue(
          issnLinking.matches(ISSN_PATTERN),
          "Linking ISSN格式无效，必须符合 'XXXX-XXXX' 格式：%s", issnLinking);
    }

    // 标准化为大写（X可能是小写）
    issn = issn.toUpperCase();
    if (issnLinking != null) {
      issnLinking = issnLinking.toUpperCase();
    }
  }

  /**
   * 创建ISSN信息（不含Linking ISSN）。
   *
   * @param issn ISSN号码
   * @param issnType ISSN类型
   * @return ISSN信息值对象
   * @throws IllegalArgumentException 如果ISSN格式无效
   */
  public static IssnInfo of(String issn, IssnType issnType) {
    return new IssnInfo(issn, issnType, null);
  }

  /**
   * 创建ISSN信息（含Linking ISSN）。
   *
   * @param issn ISSN号码
   * @param issnType ISSN类型
   * @param issnLinking Linking ISSN
   * @return ISSN信息值对象
   * @throws IllegalArgumentException 如果ISSN格式无效
   */
  public static IssnInfo of(String issn, IssnType issnType, String issnLinking) {
    return new IssnInfo(issn, issnType, issnLinking);
  }

  /**
   * 创建纸质版ISSN。
   *
   * @param issn ISSN号码
   * @return ISSN信息值对象
   */
  public static IssnInfo forPrint(String issn) {
    return new IssnInfo(issn, IssnType.PRINT, null);
  }

  /**
   * 创建电子版ISSN。
   *
   * @param issn ISSN号码
   * @return ISSN信息值对象
   */
  public static IssnInfo forElectronic(String issn) {
    return new IssnInfo(issn, IssnType.ELECTRONIC, null);
  }

  /**
   * 判断是否为纸质版ISSN。
   *
   * @return true 如果为纸质版
   */
  public boolean isPrint() {
    return issnType.isPrint();
  }

  /**
   * 判断是否为电子版ISSN。
   *
   * @return true 如果为电子版
   */
  public boolean isElectronic() {
    return issnType.isElectronic();
  }

  /**
   * 判断是否有Linking ISSN。
   *
   * @return true 如果有Linking ISSN
   */
  public boolean hasLinking() {
    return StrUtil.isNotBlank(issnLinking);
  }

  /**
   * 获取显示文本。
   *
   * @return 格式化的ISSN信息
   */
  public String toDisplayString() {
    if (hasLinking()) {
      return String.format("%s (%s, linking: %s)", issn, issnType.getCode(), issnLinking);
    }
    return String.format("%s (%s)", issn, issnType.getCode());
  }

  /**
   * 验证ISSN校验位（最后一位）。
   *
   * <p>ISSN校验算法：前7位数字乘以权重（8,7,6,5,4,3,2），求和，模11，用11减去余数得到校验位（如果是10则用X表示）。
   *
   * @return true 如果校验位正确
   */
  public boolean isChecksumValid() {
    String digits = issn.replace("-", "");
    if (digits.length() != 8) {
      return false;
    }

    int sum = 0;
    for (int i = 0; i < 7; i++) {
      sum += Character.getNumericValue(digits.charAt(i)) * (8 - i);
    }

    int checksum = (11 - (sum % 11)) % 11;
    char expectedCheck = (checksum == 10) ? 'X' : (char) ('0' + checksum);
    return digits.charAt(7) == expectedCheck;
  }
}
