package com.patra.starter.feign.error.exception;

import com.patra.common.error.problem.ErrorKeys;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.ProblemDetail;

/**
 * Exception raised when a Feign client receives an error response from a downstream service.
 *
 * <p>Intended for adapter-layer code only; application and domain layers should translate it into
 * context-specific failures. The exception exposes downstream metadata such as business error code,
 * HTTP status, trace identifier, and the {@link ProblemDetail} extension map.
 *
 * <p>Constructed by {@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder} and
 * often inspected via {@link com.patra.starter.feign.error.util.RemoteErrorHelper}.
 */
@Getter
public class RemoteCallException extends RuntimeException {

  /** Business error code returned by the downstream service (may be {@code null}). */
  private final String errorCode;

  /** HTTP status code of the downstream response. */
  private final int httpStatus;

  /** Feign method key that triggered the call. */
  private final String methodKey;

  /** Trace identifier extracted from downstream headers or payload (may be {@code null}). */
  private final String traceId;

  /** Additional ProblemDetail extension attributes returned by the downstream service. */
  private final Map<String, Object> extensions;

  /**
   * Build an exception from a downstream {@link ProblemDetail}, extracting the error code, trace
   * identifier, and extension properties.
   *
   * @param problemDetail ProblemDetail returned by the downstream service
   * @param methodKey Feign method key associated with the call
   */
  public RemoteCallException(ProblemDetail problemDetail, String methodKey) {
    super(problemDetail.getDetail());
    this.httpStatus = problemDetail.getStatus();
    this.methodKey = methodKey;

    // Extract error code and trace information from the extension map.
    Map<String, Object> properties = problemDetail.getProperties();
    if (properties == null) {
      properties = Collections.emptyMap();
    }
    this.errorCode = (String) properties.get(ErrorKeys.CODE);
    this.traceId = (String) properties.get(ErrorKeys.TRACE_ID);

    // Copy all extension fields for later inspection.
    this.extensions = new HashMap<>(properties);
  }

  /**
   * Build an exception for non-ProblemDetail responses (strict mode fallback or tolerant-mode
   * scenarios).
   *
   * @param httpStatus HTTP status returned by the downstream service
   * @param message Reason phrase or synthesized error message
   * @param methodKey Feign method key
   * @param traceId Trace identifier extracted from response headers, if any
   */
  public RemoteCallException(int httpStatus, String message, String methodKey, String traceId) {
    super(message);
    this.httpStatus = httpStatus;
    this.methodKey = methodKey;
    this.traceId = traceId;
    this.errorCode = null;
    this.extensions = Collections.emptyMap();
  }

  /**
   * Construct an exception with explicit values for all fields.
   *
   * @param errorCode Business error code (optional)
   * @param httpStatus HTTP status code
   * @param message Error message
   * @param methodKey Feign method key
   * @param traceId Trace identifier (optional)
   * @param extensions ProblemDetail extensions (nullable)
   */
  public RemoteCallException(
      String errorCode,
      int httpStatus,
      String message,
      String methodKey,
      String traceId,
      Map<String, Object> extensions) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.methodKey = methodKey;
    this.traceId = traceId;
    this.extensions = extensions != null ? new HashMap<>(extensions) : Collections.emptyMap();
  }

  /**
   * @return {@code true} if a non-empty business error code is present.
   */
  public boolean hasErrorCode() {
    return errorCode != null && !errorCode.trim().isEmpty();
  }

  /**
   * @return {@code true} if a trace identifier is available.
   */
  public boolean hasTraceId() {
    return traceId != null && !traceId.trim().isEmpty();
  }

  /**
   * Retrieve a ProblemDetail extension value.
   *
   * @param key Extension key
   * @return Extension value or {@code null} when not present
   */
  public Object getExtension(String key) {
    return extensions.get(key);
  }

  /**
   * Retrieve a typed ProblemDetail extension value.
   *
   * @param key Extension key
   * @param type Desired value type
   * @param <T> Type parameter
   * @return Converted value, or {@code null} when absent or type mismatch
   */
  @SuppressWarnings("unchecked")
  public <T> T getExtension(String key, Class<T> type) {
    Object value = extensions.get(key);
    if (value != null && type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }

  /**
   * @return An immutable copy of the ProblemDetail extensions.
   */
  public Map<String, Object> getAllExtensions() {
    return Collections.unmodifiableMap(extensions);
  }
}
