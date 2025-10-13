package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * Exception raised when scheduler parameters are invalid.
 *
 * <p>Scenario: XXL-Job or internal schedulers invoke handlers with JSON/KV parameters that miss
 * required fields, violate format constraints, exceed limits, or conflict with the current
 * operation type. The error is attributable to caller input; return immediately and log a warning
 * rather than retrying.
 *
 * <p>Remediation suggestions:
 *
 * <ul>
 *   <li>Verify the scheduler task configuration and templates remain aligned.
 *   <li>Apply schema validation to JSON parameters in the adapter layer to fail fast.
 *   <li>Instrument metrics counting missing fields to detect template drift.
 * </ul>
 */
public class IngestScheduleParameterException extends IngestException implements HasErrorTraits {

  /**
   * Construct the exception with a human-readable message.
   *
   * @param message descriptive message
   */
  public IngestScheduleParameterException(String message) {
    super(message);
  }

  /**
   * Construct the exception with a message and underlying cause (for example, JSON parsing
   * failures).
   *
   * @param message descriptive message
   * @param cause underlying exception
   */
  public IngestScheduleParameterException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.RULE_VIOLATION);
  }
}
