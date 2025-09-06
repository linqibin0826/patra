package com.patra.registry.infra.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.domain.model.enums.EmitTarget;
import com.patra.registry.domain.model.enums.MatchType;
import com.patra.registry.domain.model.enums.QueryOperation;
import com.patra.registry.domain.model.enums.ValueType;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据源查询渲染规则数据对象
 * docref: /docs/schema/tables.inventory.md#reg_source_query_render_rule
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SourceQueryRenderRuleDO extends BaseDO {
    
    /**
     * 逻辑外键→reg_literature_provenance.id
     */
    private Long literatureProvenanceId;
    
    /**
     * 内部字段键，如 ti/ab/tiab/la/pt/dp/owner
     */
    private String fieldKey;
    
    /**
     * 操作符
     */
    private QueryOperation op;
    
    /**
     * 匹配策略（TERM 专用；NULL=不区分）
     */
    private MatchType matchType;
    
    /**
     * 是否针对取反情形的规则；NULL=不区分
     */
    private Boolean negated;
    
    /**
     * RANGE 值类型；其他 OP 可为空
     */
    private ValueType valueType;
    
    /**
     * 渲染输出到 query 或 params
     */
    private EmitTarget emit;
    
    /**
     * 优先级（值越大越优先）
     */
    private Integer priority;
    
    /**
     * query 模板（支持 helper：{{q v}}/{{lower ...}} 等）
     */
    private String template;
    
    /**
     * IN/集合展开的单项模板（可选）
     */
    private String itemTemplate;
    
    /**
     * 集合项连接符（如 " OR "）
     */
    private String joiner;
    
    /**
     * 集合是否用括号包裹
     */
    private Boolean wrapGroup;
    
    /**
     * 参数映射（标准键→供应商参数名），如 {"from->mindate":"mindate"}
     */
    private JsonNode params;
    
    /**
     * 可选渲染函数名（如 pubmedDatetypeRenderer）
     */
    private String fn;
    
    /**
     * json数组，备注/变更说明
     */
    private JsonNode recordRemarks;
}
