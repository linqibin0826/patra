package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO representing the validation result for a dictionary reference.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>typeCode - dictionary type identifier
 *   <li>itemCode - dictionary item identifier
 *   <li>valid - whether the reference is valid
 *   <li>errorMessage - validation error message when {@code valid} is false
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryValidationResp(
    String typeCode, String itemCode, boolean valid, String errorMessage) {}
