package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Persistence entity mapped to {@code reg_provenance}.
 * <p>Represents the root provenance record that all downstream configurations reference.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_provenance")
public class RegProvenanceDO extends BaseDO {

    /**
     * Stable provenance code used as business identifier.
     */
    @TableField("provenance_code")
    private String provenanceCode;

    /**
     * Human-readable provenance name.
     */
    @TableField("provenance_name")
    private String provenanceName;

    /**
     * Default base URL used when operation-level overrides are absent.
     */
    @TableField("base_url_default")
    private String baseUrlDefault;

    /**
     * Default timezone applied to date fields (IANA format).
     */
    @TableField("timezone_default")
    private String timezoneDefault;

    /**
     * Optional link to official provider documentation.
     */
    @TableField("docs_url")
    private String docsUrl;

    /**
     * Flag indicating if the provenance is active.
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * Lifecycle status code aligned with dictionary {@code lifecycle_status}.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
