package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典项别名实体（表 sys_dict_item_alias）。
 *
 * <p>为外部系统集成提供映射（只读，用于 CQRS 查询侧）。</p>
 *
 * <p>规则示意：
 * - (source_system, external_code) 全局唯一
 * - 通过别名对接外部系统而不改动业务表结构
 * - 常见来源：pubmed、crossref、legacy_v1 等
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item_alias")
public class RegSysDictItemAliasDO extends BaseDO {

    /**
     * Dictionary item ID that this alias maps to.
     * Logical foreign key reference to sys_dict_item.id.
     * Establishes the relationship between external codes and internal dictionary items.
     */
    @TableField("item_id")
    private Long itemId;

    /**
     * 外部映射来源系统标识。
     * 标识该别名来自哪个外部系统或遗留应用。
     * 格式建议：小写+下划线或中划线（如 "pubmed"、"crossref"、"legacy_v1"）。
     * 用于为外部编码命名空间化，避免不同系统冲突。
     */
    @TableField("source_system")
    private String sourceSystem;

    /**
     * 外部系统的编码/取值。
     * 外部系统用来表示该字典项的实际编码；与 source_system 组合后在全局范围内唯一。
     * 用作外部值映射至内部字典项的关键键值。
     */
    @TableField("external_code")
    private String externalCode;

    /**
     * 外部系统的展示标签（可选）。
     * 存放外部系统的人类可读名称，便于理解外部术语。
     */
    @TableField("external_label")
    private String externalLabel;

    /**
     * 别名映射说明。
     * 记录差异、兼容性说明、来源链接等补充信息，便于理解映射上下文。
     */
    @TableField("notes")
    private String notes;
}
