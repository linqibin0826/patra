package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据表 {@code reg_provenance} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_provenance")
public class RegProvenanceDO extends BaseDO {

    @TableField("provenance_code")
    private String provenanceCode;

    @TableField("provenance_name")
    private String provenanceName;

    @TableField("base_url_default")
    private String baseUrlDefault;

    @TableField("timezone_default")
    private String timezoneDefault;

    @TableField("docs_url")
    private String docsUrl;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
