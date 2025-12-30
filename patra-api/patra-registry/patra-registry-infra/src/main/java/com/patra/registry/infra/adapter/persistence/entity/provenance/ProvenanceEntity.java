package com.patra.registry.infra.adapter.persistence.entity.provenance;

import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 数据源 JPA 实体，映射到表 `reg_provenance`。
///
/// 表示所有下游配置引用的根数据源记录。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_provenance")
public class ProvenanceEntity extends SoftDeletableJpaEntity {

  /// 用作业务标识符的稳定数据源代码。
  @Column(name = "provenance_code", nullable = false, length = 20)
  private String provenanceCode;

  /// 可读的数据源名称。
  @Column(name = "provenance_name", length = 100)
  private String provenanceName;

  /// 当操作级覆盖不存在时使用的默认基础 URL。
  @Column(name = "base_url_default", length = 500)
  private String baseUrlDefault;

  /// 应用于日期字段的默认时区(IANA 格式)。
  @Column(name = "timezone_default", length = 50)
  private String timezoneDefault;

  /// 指向官方提供方文档的可选链接。
  @Column(name = "docs_url", length = 500)
  private String docsUrl;

  /// 指示数据源是否激活的标志。
  @Column(name = "is_active")
  private Boolean isActive;

  /// 与字典 `lifecycle_status` 对齐的生命周期状态代码。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
