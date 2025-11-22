package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

/// 数据源值对象,表示外部数据源的核心元数据。
///
/// **不可变性**:此对象一旦创建不可修改,通过值语义比较相等性。作为所有数据源配置的 根实体,为下游配置(分页、HTTP、重试等)提供唯一标识和基础信息。
///
/// **业务约束**:
///
/// - 数据源ID必须为正整数,作为全局唯一标识符
///   - 数据源代码(code)必须全局唯一且稳定不变,如`pubmed`、`crossref`
///   - 数据源名称、默认时区不可为空白字符串
///   - 默认基础URL、文档URL为可选项,允许为空
///   - 所有字符串字段自动执行trim操作
///   - 仅激活状态的数据源才能被用于数据采集流程
///
/// @param id 数据源主键,所有下游配置引用的唯一标识符,必须为正整数
/// @param code 数据源代码,全局唯一且稳定(如`pubmed`、`crossref`),用于编程引用和约束
/// @param name 数据源显示名称(如"PubMed"、"Crossref"),便于人工阅读和界面展示
/// @param baseUrlDefault 默认基础URL,与端点路径组合形成完整API地址,可被HTTP策略覆盖,可为null
/// @param timezoneDefault 默认时区(IANA格式,如`UTC`、`Asia/Shanghai`),用于时间窗口计算
/// @param docsUrl 官方文档或参考URL,用于故障排查和API验证,可为null
/// @param active 激活标志,`true`表示激活可用,`false`表示停用
/// @param lifecycleStatusCode 生命周期状态代码(字典值,如`ACTIVE`),用于精细化状态管理
/// @author Patra Team
/// @since 2.0
public record Provenance(
    Long id,
    String code,
    String name,
    String baseUrlDefault,
    String timezoneDefault,
    String docsUrl,
    boolean active,
    String lifecycleStatusCode) {
  /// 规范构造器,强制执行数据源值对象的业务约束。
  ///
  /// 验证规则:
  ///
  /// - 数据源ID必须为正整数
  ///   - 数据源代码、名称、时区、生命周期状态不可为空白
  ///   - 所有字符串字段自动trim去除首尾空白
  ///   - 可选字段(基础URL、文档URL)允许为null
  ///
  /// @throws DomainValidationException 如果验证失败
  public Provenance(
      Long id,
      String code,
      String name,
      String baseUrlDefault,
      String timezoneDefault,
      String docsUrl,
      boolean active,
      String lifecycleStatusCode) {
    DomainValidationException.positive(id, "Provenance id");
    String codeTrimmed = DomainValidationException.notBlank(code, "Provenance code");
    String nameTrimmed = DomainValidationException.notBlank(name, "Provenance name");
    String tzTrimmed = DomainValidationException.notBlank(timezoneDefault, "Timezone");
    String lifecycleTrimmed =
        DomainValidationException.notBlank(lifecycleStatusCode, "Lifecycle status code");

    this.id = id;
    this.code = codeTrimmed;
    this.name = nameTrimmed;
    this.baseUrlDefault = baseUrlDefault != null ? baseUrlDefault.trim() : null;
    this.timezoneDefault = tzTrimmed;
    this.docsUrl = docsUrl != null ? docsUrl.trim() : null;
    this.active = active;
    this.lifecycleStatusCode = lifecycleTrimmed;
  }

  /// 判断数据源是否处于激活状态。
  ///
  /// 仅激活状态的数据源才能被用于数据采集流程。
  ///
  /// @return 如果数据源激活则返回`true`,否则返回`false`
  public boolean isActive() {
    return active;
  }
}
