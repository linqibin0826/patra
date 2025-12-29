package com.patra.catalog.domain.model.vo.common;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 来源标准值对象。
///
/// 表示外部数据源使用的字典值格式或规范。用于标识原始数据采用的编码标准，
/// 以便字典解析服务将其转换为系统规范标准。
///
/// **设计原则**：
///
/// - **不可变性**：Record 自动提供
/// - **类型安全**：封装字符串，避免直接传递裸字符串
/// - **自描述**：预定义常量提供常用标准
///
/// **常用标准**：
///
/// | 标准代码 | 说明 | 使用场景 |
/// |---------|------|---------|
/// | NAME_EN | 英文名称 | PubMed LSIOU 国家字段 |
/// | ISO_3166_1_ALPHA2 | ISO 3166-1 alpha-2 | 国家代码规范标准 |
/// | ISO_3166_1_ALPHA3 | ISO 3166-1 alpha-3 | 三字母国家代码 |
/// | ISO_639_3 | ISO 639-3 三字母代码 | PubMed 语言代码 |
/// | BCP_47 | BCP 47 语言标签 | 语言规范标准 |
///
/// **使用示例**：
///
/// ```java
/// // 使用预定义标准
/// SourceStandard nameEn = SourceStandard.NAME_EN;
/// SourceStandard iso639 = SourceStandard.ISO_639_3;
///
/// // 创建自定义标准
/// SourceStandard custom = SourceStandard.of("CUSTOM_STANDARD");
///
/// // 在字典解析中使用
/// Map<String, String> result = dictionaryResolver.resolve(
///     DictionaryType.COUNTRY,
///     SourceStandard.NAME_EN,
///     rawValues
/// );
/// ```
///
/// @param code 标准代码（对应 patra-registry 中的 sys_reference_standard.standard_code）
/// @author linqibin
/// @since 0.1.0
public record SourceStandard(String code) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  // ==================== 预定义常量 ====================

  /// 英文名称标准（用于 PubMed LSIOU 国家字段）。
  public static final SourceStandard NAME_EN = new SourceStandard("NAME_EN");

  /// ISO 3166-1 alpha-2 国家代码标准（规范标准）。
  public static final SourceStandard ISO_3166_1_ALPHA2 = new SourceStandard("ISO_3166_1_ALPHA2");

  /// ISO 3166-1 alpha-3 三字母国家代码标准。
  public static final SourceStandard ISO_3166_1_ALPHA3 = new SourceStandard("ISO_3166_1_ALPHA3");

  /// ISO 639-3 三字母语言代码标准（用于 PubMed 语言字段）。
  public static final SourceStandard ISO_639_3 = new SourceStandard("ISO_639_3");

  /// BCP 47 语言标签标准（语言规范标准）。
  public static final SourceStandard BCP_47 = new SourceStandard("BCP_47");

  // ==================== 构造与工厂方法 ====================

  /// 紧凑构造器：验证并规范化标准代码。
  ///
  /// @throws IllegalArgumentException 如果标准代码为空
  public SourceStandard {
    Assert.notBlank(code, "来源标准代码不能为空");
    code = code.trim();
  }

  /// 创建来源标准值对象。
  ///
  /// @param code 标准代码
  /// @return 来源标准值对象
  public static SourceStandard of(String code) {
    return new SourceStandard(code);
  }

  // ==================== 重写方法 ====================

  @Override
  public String toString() {
    return code;
  }
}
