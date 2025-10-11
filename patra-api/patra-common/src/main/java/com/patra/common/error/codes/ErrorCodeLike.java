package com.patra.common.error.codes;

/**
 * Contract for structured business error codes leveraged by the unified error-handling system.
 *
 * <p>Implementations must expose a globally unique identifier to power error resolution, mapping, and client-side handling.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ErrorCodeLike {
    
    /**
     * Returns the canonical error-code string.
     *
     * <p>Follow the shared naming pattern (for example, {@code REG-0404} or
     * {@code ING-1201}) so that responses remain readable for humans and easy to
     * parse programmatically.</p>
     */
    String code();

    /**
     * Returns the HTTP status (100–599) associated with this code.
     *
     * <p>Used when rendering HTTP responses; other transports may ignore or override this mapping.</p>
     */
    int httpStatus();
}
