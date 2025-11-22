package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 数据库实体,映射到表 `reg_provenance`。
/// 
/// 表示所有下游配置引用的根数据源记录。
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_provenance")
public class RegProvenanceDO extends BaseDO {

  /// 用作业务标识符的稳定数据源代码。
  @TableField("provenance_code")
  private String provenanceCode;

  /// 可读的数据源名称。
  @TableField("provenance_name")
  private String provenanceName;

  /// 当操作级覆盖不存在时使用的默认基础 URL。
  @TableField("base_url_default")
  private String baseUrlDefault;

  /// 应用于日期字段的默认时区(IANA 格式)。
  @TableField("timezone_default")
  private String timezoneDefault;

  /// 指向官方提供方文档的可选链接。
  @TableField("docs_url")
  private String docsUrl;

  /// 指示数据源是否激活的标志。
  @TableField("is_active")
  private Boolean isActive;

  /// 与字典 `lifecycle_status` 对齐的生命周期状态代码。
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
