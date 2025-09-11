package com.patra.registry.api.rpc.dto;

/**
 * 查询渲染规则对外 DTO。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record QueryRenderRuleApiResp(
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
