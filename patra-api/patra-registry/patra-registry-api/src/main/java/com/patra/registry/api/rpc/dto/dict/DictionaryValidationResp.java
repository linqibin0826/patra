package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO representing the validation result for a dictionary reference.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>typeCode - dictionary type identifier</li>
 *   <li>itemCode - dictionary item identifier</li>
 *   <li>valid - whether the reference is valid</li>
 *   <li>errorMessage - validation error message when {@code valid} is false</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryValidationResp(
        String typeCode,
        String itemCode,
        boolean valid,
        String errorMessage
) {
}
