package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/**
 * 语言信息值对象。封装文献的三层语言标准化结构。
 *
 * <p>三层设计原理：
 *
 * <ul>
 *   <li><b>raw</b>：外部系统采集的原始值（如 "Chinese", "English"）
 *   <li><b>code</b>：应用层标准化的语言代码（如 "zh-CN", "en-US"，遵循 BCP 47）
 *   <li><b>base</b>：基础语种代码（如 "zh", "en"，用于分组和统计）
 * </ul>
 *
 * <p>设计优势：
 *
 * <ul>
 *   <li>保留原始数据可追溯性（raw）
 *   <li>确保业务逻辑使用标准代码（code）
 *   <li>支持按语系分组查询（base）
 *   <li>自动一致性验证（base 必须与 code 前缀匹配）
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 创建标准语言信息
 * LanguageInfo lang1 = LanguageInfo.of("Chinese", "zh-CN", "zh");
 *
 * // base 自动推导
 * LanguageInfo lang2 = LanguageInfo.of("English", "en-US");  // base = "en"
 *
 * // 业务判断
 * if (lang1.isChinese()) {
 *     System.out.println("这是中文文献");
 * }
 * }</pre>
 *
 * @param raw 原始语言值（外部采集，可为空）
 * @param code 标准语言代码（BCP 47，必填）
 * @param base 基础语种代码（必填，自动推导或显式指定）
 * @author linqibin
 * @since 0.1.0
 */
public record LanguageInfo(String raw, String code, String base) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * 紧凑构造器：验证语言信息的有效性和一致性。
   *
   * @throws IllegalArgumentException 如果 code 或 base 为空，或 base 与 code 不一致
   */
  public LanguageInfo {
    Assert.notBlank(code, "语言代码（code）不能为空");
    Assert.notBlank(base, "基础语种代码（base）不能为空");

    // 验证 base 是 code 的前缀
    Assert.isTrue(
        code.startsWith(base),
        "基础语种代码（base:%s）必须是语言代码（code:%s）的前缀", base, code);

    // 验证 base 长度（通常为 2-3 位）
    Assert.isTrue(
        base.length() >= 2 && base.length() <= 3,
        "基础语种代码（base）长度必须在 2-3 位之间：%s", base);
  }

  /**
   * 创建语言信息，自动从 code 推导 base。
   *
   * @param raw 原始语言值
   * @param code 标准语言代码（如 "zh-CN", "en-US"）
   * @return 语言信息值对象
   * @throws IllegalArgumentException 如果 code 格式无效
   */
  public static LanguageInfo of(String raw, String code) {
    Assert.notBlank(code, "语言代码（code）不能为空");
    String base = extractBase(code);
    return new LanguageInfo(raw, code, base);
  }

  /**
   * 创建语言信息，显式指定所有字段。
   *
   * @param raw 原始语言值
   * @param code 标准语言代码
   * @param base 基础语种代码
   * @return 语言信息值对象
   * @throws IllegalArgumentException 如果 code、base 为空或不一致
   */
  public static LanguageInfo of(String raw, String code, String base) {
    return new LanguageInfo(raw, code, base);
  }

  /**
   * 创建仅包含 code 的语言信息（raw 为空）。
   *
   * @param code 标准语言代码
   * @return 语言信息值对象
   */
  public static LanguageInfo ofCode(String code) {
    return of(null, code);
  }

  /**
   * 判断是否有原始值。
   *
   * @return true 如果 raw 不为空
   */
  public boolean hasRaw() {
    return StrUtil.isNotBlank(raw);
  }

  /**
   * 从语言代码中提取基础语种代码。
   *
   * <p>示例：
   *
   * <ul>
   *   <li>"zh-CN" → "zh"
   *   <li>"en-US" → "en"
   *   <li>"pt-BR" → "pt"
   *   <li>"zh" → "zh"（已经是基础代码）
   * </ul>
   *
   * @param code 语言代码
   * @return 基础语种代码
   */
  private static String extractBase(String code) {
    if (code.contains("-")) {
      return code.substring(0, code.indexOf('-'));
    }
    return code;
  }

  /**
   * 获取显示文本。
   *
   * @return 格式化的语言信息
   */
  public String toDisplayString() {
    if (hasRaw()) {
      return String.format("%s (%s)", raw, code);
    }
    return code;
  }
}
