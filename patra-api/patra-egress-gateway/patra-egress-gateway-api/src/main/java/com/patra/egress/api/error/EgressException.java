package com.patra.egress.api.error;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Base exception for egress gateway errors
 *
 * <p>Extends ApplicationException to carry structured error codes that can be mapped to HTTP
 * responses and client-friendly error messages.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class EgressException extends ApplicationException {

  /**
   * Constructs an egress exception with error code and message
   *
   * @param errorCode structured error code
   * @param message error message
   */
  public EgressException(ErrorCodeLike errorCode, String message) {
    super(errorCode, message);
  }

  /**
   * Constructs an egress exception with error code, message, and cause
   *
   * @param errorCode structured error code
   * @param message error message
   * @param cause underlying cause
   */
  public EgressException(ErrorCodeLike errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }
}
