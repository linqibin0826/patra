package com.patra.registry.api.rpc.dto.dict;

/**
 * Request payload representing a dictionary reference that needs validation.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>typeCode - dictionary type identifier being referenced
 *   <li>itemCode - dictionary item identifier being referenced
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryReferenceReq(String typeCode, String itemCode) {
  /**
   * Canonical constructor performing null/blank validation and trimming.
   *
   * @param typeCode dictionary type identifier being referenced
   * @param itemCode dictionary item identifier being referenced
   */
  public DictionaryReferenceReq {
    if (typeCode == null || typeCode.trim().isEmpty()) {
      throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
    }
    if (itemCode == null || itemCode.trim().isEmpty()) {
      throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
    }
    typeCode = typeCode.trim();
    itemCode = itemCode.trim();
  }
}
