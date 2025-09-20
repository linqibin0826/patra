/**
 * Registry service error handling API.
 * 
 * This package contains the error code catalog and related contracts for the Registry service.
 * The error codes follow a structured format and are designed to provide consistent,
 * machine-readable error identification across all Registry service operations.
 * 
 * <h2>Error Code Format</h2>
 * All Registry error codes follow the format: {@code REG-NNNN}
 * <ul>
 *   <li>{@code REG} - Registry service context prefix</li>
 *   <li>{@code NNNN} - Four-digit numeric code</li>
 * </ul>
 * 
 * <h2>Error Code Categories</h2>
 * <ul>
 *   <li>{@code 0xxx} - Common HTTP-aligned codes (REG-0400 to REG-0504)</li>
 *   <li>{@code 1xxx} - Business-specific codes organized by domain</li>
 * </ul>
 * 
 * <h2>Append-Only Policy</h2>
 * This error catalog follows an append-only principle:
 * <ul>
 *   <li>New error codes can be added as needed</li>
 *   <li>Existing codes must never be removed or modified</li>
 *   <li>This ensures API stability and backward compatibility</li>
 * </ul>
 * 
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.registry.api.error;