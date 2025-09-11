package com.patra.registry.domain.model.vo;

import com.patra.registry.domain.model.enums.EmitTarget;
import com.patra.registry.domain.model.enums.MatchType;
import com.patra.registry.domain.model.enums.QueryOperation;
import com.patra.registry.domain.model.enums.ValueType;
import lombok.Builder;
import lombok.Value;

/**
 * 查询渲染规则值对象。
 * <p>描述将内部查询表达渲染为上游 API 可接受的 query/params 文本的规则模板。
 */
@Value
@Builder
public class QueryRenderRule {
    
    /**
     * 内部字段键，如 ti/ab/tiab/la/pt/dp/owner
     */
    String fieldKey;
    
    /**
     * 操作符
     */
    QueryOperation op;
    
    /**
     * 匹配策略（TERM 专用；NULL=不区分）
     */
    MatchType matchType;
    
    /**
     * 是否针对取反情形的规则；NULL=不区分
     */
    Boolean negated;
    
    /**
     * RANGE 值类型；其他 OP 可为空
     */
    ValueType valueType;
    
    /**
     * 渲染输出到 query 或 params
     */
    EmitTarget emit;
    
    /**
     * 优先级（值越大越优先）
     */
    Integer priority;
    
    /**
     * query 模板（支持 helper：{{q v}}/{{lower ...}} 等）
     */
    String template;
    
    /**
     * IN/集合展开的单项模板（可选）
     */
    String itemTemplate;
    
    /**
     * 集合项连接符（如 " OR "）
     */
    String joiner;
    
    /**
     * 集合是否用括号包裹
     */
    Boolean wrapGroup;
    
    /**
     * 参数映射（标准键→供应商参数名）
     */
    String params;
    
    /**
     * 可选渲染函数名（如 pubmedDatetypeRenderer）
     */
    String fn;
}
