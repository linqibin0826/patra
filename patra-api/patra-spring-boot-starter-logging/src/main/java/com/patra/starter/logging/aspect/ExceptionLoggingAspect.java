package com.patra.starter.logging.aspect;

import com.patra.common.logging.sanitizer.LogSanitizer;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aspect for automatic exception logging with context capture.
 *
 * <p>This aspect intercepts all exceptions thrown by application methods and logs them with:
 *
 * <ul>
 *   <li>Full stack trace
 *   <li>Method signature and arguments (sanitized)
 *   <li>Trace context from MDC (automatic via logback pattern)
 *   <li>Business operation context
 * </ul>
 *
 * <p>Applies to all methods in {@code com.patra} package.
 *
 * @see LogSanitizer
 * @since 0.1.0
 */
@Aspect
public class ExceptionLoggingAspect {

  private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingAspect.class);

  private final LogSanitizer logSanitizer;

  public ExceptionLoggingAspect(LogSanitizer logSanitizer) {
    this.logSanitizer = logSanitizer;
  }

  /**
   * Logs exceptions thrown by any method in the application.
   *
   * <p>Trace context (traceId, spanId, correlationId) is automatically included via MDC in the log
   * pattern.
   *
   * @param joinPoint The join point where exception was thrown
   * @param exception The exception
   */
  @AfterThrowing(pointcut = "execution(* com.patra..*.*(..))", throwing = "exception")
  public void logException(JoinPoint joinPoint, Throwable exception) {

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String className = signature.getDeclaringType().getSimpleName();
    String methodName = signature.getName();
    Object[] args = joinPoint.getArgs();

    // Sanitize arguments before logging
    String sanitizedArgs = sanitizeArguments(args);

    // Log with full context (MDC provides trace context automatically)
    log.error(
        "Exception in business operation: {}.{} with arguments: {} | Exception: {}",
        className,
        methodName,
        sanitizedArgs,
        exception.getClass().getSimpleName(),
        exception);
  }

  /**
   * Sanitizes method arguments to remove sensitive data.
   *
   * @param args Method arguments
   * @return Sanitized string representation
   */
  private String sanitizeArguments(Object[] args) {
    if (args == null || args.length == 0) {
      return "[]";
    }

    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < args.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }

      Object arg = args[i];
      if (arg == null) {
        sb.append("null");
      } else {
        // Sanitize each argument
        String argStr = logSanitizer.sanitizeObject(arg);
        sb.append(argStr);
      }
    }
    sb.append("]");

    return sb.toString();
  }
}
