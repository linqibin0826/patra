package com.patra.registry.infra.persistence.entity.dictionary;

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
 * 字典项实体（表 sys_dict_item）。
 *
 * <p>隶属于某类型的具体条目；CQRS 查询侧只读使用。</p>
 *
 * <p>数据库层规则（约束示意）：
 * - 每个类型最多一个默认项（由 default_key 生成列配合唯一约束实现）
 * - 同一类型内 item_code 唯一
 * - item_code 建议大写下划线风格（如 "GET", "PAGE_NUMBER"）
 *
 * </p>
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class RegSysDictItemDO extends BaseDO {

    /**
     * 父类型 ID。
     * 逻辑外键，指向 sys_dict_type.id，用于建立类型与项的层级关系。
     */
    @TableField("type_id")
    private Long typeId;

    /**
     * 字典项编码（稳定业务键）。
     * 建议格式：大写+下划线（如 "GET"、"POST"、"PAGE_NUMBER"）。
     * 同一类型内唯一，作为项的主要业务标识。
     */
    @TableField("item_code")
    private String itemCode;

    /**
     * 项名称（展示用）。
     * 用于 UI 下拉/列表与用户文档，清晰标识项含义。
     */
    @TableField("item_name")
    private String itemName;

    /**
     * 短名称/缩写（紧凑 UI 场景，非必填）。
     */
    @TableField("short_name")
    private String shortName;

    /**
     * 项描述。
     * 说明语义、边界与兼容性，便于理解何时使用该值。
     */
    @TableField("description")
    private String description;

    /**
     * 展示排序。
     * 值越小越靠前；默认 100，便于灵活插入新项。
     */
    @TableField("display_order")
    private Integer displayOrder;

    /**
     * 是否为该类型的默认项。
     * 每个类型仅允许一个默认项（数据库层约束保证）。
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 是否启用。
     * 禁用（0）不参与业务；启用（1）可供选择/校验；可临时停用而不删除。
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 标签颜色（展示）。
     * 支持十六进制色值（#AABBCC）或语义色名，供前端区分展示。
     */
    @TableField("label_color")
    private String labelColor;

    /**
     * 图标名称（展示）。
     * 引用前端图标库的标识，增强视觉表达。
     */
    @TableField("icon_name")
    private String iconName;

    /**
     * 扩展属性（JSON）。
     * 存放业务特定键值（如别名、提示、兼容标记等）；使用 JsonNode 灵活承载。
     */
    @TableField("attributes_json")
    private JsonNode attributesJson;

    /**
     * 生成列：用于唯一默认项约束。
     * 当 (is_default=1 AND enabled=1 AND deleted=0) 时计算为 type_id，否则为 NULL；
     * 由数据库唯一约束保证每类型仅一个默认项；该字段不应手工赋值。
     */
    @TableField("default_key")
    private Long defaultKey;
}
