package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典类型实体（表 sys_dict_type）。
 *
 * <p>CQRS 查询侧只读使用，承载类型元数据。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class RegSysDictTypeDO extends BaseDO {

    /**
     * 字典类型编码（跨环境稳定的业务键）。
     * 建议格式：小写+下划线（如 "http_method"、"endpoint_usage"）。
     * 作为类型的主要业务标识。
     */
    @TableField("type_code")
    private String typeCode;

    /**
     * 类型名称（展示用）。
     * 用于 UI 组件与文档中的友好展示。
     */
    @TableField("type_name")
    private String typeName;

    /**
     * 类型描述。
     * 说明用途、使用场景与边界。
     */
    @TableField("description")
    private String description;

    /**
     * 是否允许自定义项。
     * true（1）允许业务扩展；false（0）仅系统预置。
     */
    @TableField("allow_custom_items")
    private Boolean allowCustomItems;

    /**
     * 是否系统内置类型。
     * 系统类型（1）由平台管理，通常不允许业务修改；业务类型（0）可按需自定义。
     */
    @TableField("is_system")
    private Boolean isSystem;

    /**
     * 扩展元数据（JSON）。
     * 例如 UI 颜色、图标、排序策略等；使用 JsonNode 承载，灵活且无需严格 schema。
     */
    @TableField("reserved_json")
    private JsonNode reservedJson;
}
