package com.patra.registry.contract.query.view;

/**
 * 查询渲染规则读侧视图。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record QueryRenderRuleView(
        String fieldKey,
        String op,
        String matchType,
        Boolean negated,
        String valueType,
        String emit,
        Integer priority,
        String template,
        String itemTemplate,
        String joiner,
        Boolean wrapGroup,
        String params,
        String fn
) {}
