package com.patra.registry.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Registry service error code catalog.
 *
 * <p>Error codes follow the {@code REG-NNNN} format (prefix + numeric code) and are added in an
 * append-only fashion to preserve API compatibility.
 *
 * <p>Series breakdown:
 *
 * <ul>
 *   <li>0xxx - align to generic HTTP errors (delegated to {@code HttpStdErrors})
 *   <li>1xxx - domain or business-specific errors maintained here
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum RegistryErrorCode implements ErrorCodeLike {

  // Note: 0xxx series should be produced via HttpStdErrors.of("REG") factory methods.

  // ========================================
  // Business-specific codes (1xxx series)
  // ========================================

  // Dictionary operations (14xx series)

  /** Dictionary type not found (maps to {@code DictionaryNotFoundException} at type level). */
  REG_1401("REG-1401", 404),

  /** Dictionary item not found (maps to {@code DictionaryNotFoundException} at item level). */
  REG_1402("REG-1402", 404),

  /** Dictionary item disabled (maps to {@code DictionaryItemDisabled}). */
  REG_1403("REG-1403", 422),

  /** Dictionary type already exists (maps to {@code DictionaryTypeAlreadyExists}). */
  REG_1404("REG-1404", 409),

  /** Dictionary item already exists (maps to {@code DictionaryItemAlreadyExists}). */
  REG_1405("REG-1405", 409),

  /** Dictionary type disabled (maps to {@code DictionaryTypeDisabled}). */
  REG_1406("REG-1406", 422),

  /** Dictionary validation failed (maps to {@code DictionaryValidationException}). */
  REG_1407("REG-1407", 422),

  /** Default dictionary item missing (maps to {@code DictionaryDefaultItemMissing}). */
  REG_1408("REG-1408", 422),

  /** Dictionary repository failure (maps to {@code DictionaryRepositoryException}). */
  REG_1409("REG-1409", 500),

  // Registry general operations (15xx series)

  /** Registry quota exceeded (maps to {@code RegistryQuotaExceeded}). */
  REG_1501("REG-1501", 429);

  private final String code;
  private final int httpStatus;

  /**
   * Constructs an error code with HTTP status mapping.
   *
   * @param code error code in {@code REG-NNNN} format
   * @param httpStatus associated HTTP status code
   */
  RegistryErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /**
   * Returns the error code string.
   *
   * @return error code in {@code REG-NNNN} format
   */
  @Override
  public String code() {
    return code;
  }

  /**
   * Returns the associated HTTP status code.
   *
   * @return HTTP status code
   */
  @Override
  public int httpStatus() {
    return httpStatus;
  }

  /**
   * Returns the error code string representation.
   *
   * @return error code string
   */
  @Override
  public String toString() {
    return code;
  }
}
