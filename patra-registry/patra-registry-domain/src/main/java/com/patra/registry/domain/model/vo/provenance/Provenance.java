package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

/**
 * {@code reg_provenance} 的领域值对象。
 *
 * <p>表示被所有 reg_prov_* 配置引用的根来源实体。这是外部数据源(如 PubMed、Crossref)的目录基本信息。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record Provenance(
    /* 主键;所有下游配置引用的唯一来源标识符 */
    Long id,
    /* 来源代码:全局唯一、稳定(如 pubmed/crossref);用于查找和约束 */
    String code,
    /* 来源显示名称(如 PubMed / Crossref),便于人工阅读 */
    String name,
    /* 此来源的默认基础 URL;与端点路径结合形成完整的 API URL;在未被 HTTP 策略覆盖时使用 */
    String baseUrlDefault,
    /* 默认时区(IANA TZ,如 UTC/Asia/Shanghai):用于窗口计算/显示的默认值 */
    String timezoneDefault,
    /* 官方文档/参考 URL:帮助故障排查和 API 验证 */
    String docsUrl,
    /* 此来源是否激活:true=激活,false=未激活;读端可能按此标志过滤 */
    boolean active,
    /* 生命周期状态代码(字典代码:lifecycle_status);读端仅使用 ACTIVE/有效状态 */
    String lifecycleStatusCode) {
  /**
   * 带验证的规范构造器。
   *
   * @param id 唯一来源标识符,必须为正数
   * @param code 来源代码,不能为空白
   * @param name 来源名称,不能为空白
   * @param baseUrlDefault 默认基础 URL,可为 null
   * @param timezoneDefault IANA 格式的默认时区,不能为空白
   * @param docsUrl 文档 URL,可为 null
   * @param active 激活标志
   * @param lifecycleStatusCode 字典中的生命周期状态代码,不能为空白
   * @throws DomainValidationException 验证失败时抛出
   */
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

  /**
   * 检查来源是否激活。
   *
   * @return 如果来源激活则返回 {@code true},否则返回 {@code false}
   */
  public boolean isActive() {
    return active;
  }
}
