package com.patra.registry.api.dto.dict;

/**
 * 字典类型元数据,通过内部 HTTP API 暴露。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>typeCode - 字典类型标识符
 *   <li>typeName - 人类可读的类型名称
 *   <li>description - 类型的可选描述
 *   <li>enabledItemCount - 该类型下启用的项数量
 *   <li>hasDefault - 该类型当前是否有默认项
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeResp(
    String typeCode,
    String typeName,
    String description,
    int enabledItemCount,
    boolean hasDefault) {}
