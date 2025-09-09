package com.patra.starter.mybatis.entity.BaseDO;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BaseDO：所有数据对象的基类，包含审计字段、乐观锁、逻辑删除等通用属性。
 * 使用 MyBatis-Plus 插件注解支持自动填充。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public abstract class BaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * JSON 数组，备注/变更说明。
     * 例如：[{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]
     * 推荐结合 Jackson JsonNode + TypeHandler 使用。
     */
    @TableField(value = "record_remarks")
    private String recordRemarks;

    /**
     * 创建时间（自动填充）
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 创建人ID（自动填充）
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建人姓名（自动填充）
     */
    @TableField(value = "created_by_name", fill = FieldFill.INSERT)
    private String createdByName;

    /**
     * 更新时间（自动填充）
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 更新人ID（自动填充）
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 更新人姓名（自动填充）
     */
    @TableField(value = "updated_by_name", fill = FieldFill.INSERT_UPDATE)
    private String updatedByName;

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField(value = "version")
    private Long version;

    /**
     * 请求方 IP（二进制，支持 IPv4/IPv6）
     */
    @TableField(value = "ip_address")
    private byte[] ipAddress;

    /**
     * 逻辑删除标记：0=未删除，1=已删除
     */
    @TableLogic
    @TableField(value = "deleted")
    private Boolean deleted;
}
