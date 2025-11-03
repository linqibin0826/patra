package com.patra.registry.api.dto.dict;

/**
 * 字典引用的验证结果。
 *
 * <p>字段说明:
 *
 * <ol>
 *   <li>typeCode - 字典类型标识符
 *   <li>itemCode - 字典项标识符
 *   <li>valid - 引用是否有效
 *   <li>errorMessage - 当 {@code valid} 为 false 时的验证错误消息
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryValidationResp(
    String typeCode, String itemCode, boolean valid, String errorMessage) {}
