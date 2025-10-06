/**
 * Registry service error handling API package.
 *
 * <p>This package defines the error code catalog and related contracts. Error codes follow a
 * structured format so that all Registry operations expose consistent, machine-readable
 * identifiers.</p>
 *
 * <h2>Error Code Format</h2>
 * All error codes follow {@code REG-NNNN}:
 * <ul>
 *   <li>{@code REG} — Registry service prefix</li>
 *   <li>{@code NNNN} — four-digit numeric code</li>
 * </ul>
 *
 * <h2>Series Definition</h2>
 * <ul>
 *   <li>{@code 0xxx} — generic HTTP-aligned errors produced via {@code HttpStdErrors}</li>
 *   <li>{@code 1xxx} — domain or business-specific errors maintained in the catalog</li>
 * </ul>
 *
 * <h2>Append-Only Maintenance</h2>
 * <ul>
 *   <li>New codes may be added as required</li>
 *   <li>Existing codes must not be removed or altered</li>
 *   <li>This guarantees API stability and backward compatibility</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.registry.api.error;
