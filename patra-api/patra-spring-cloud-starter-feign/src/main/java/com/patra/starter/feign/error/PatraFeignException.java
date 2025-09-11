package com.patra.starter.feign.error;

import com.patra.common.error.core.PlatformError;
import com.patra.starter.core.error.exception.PatraHttpException;

import java.io.Serial;


public class PatraFeignException extends PatraHttpException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String methodKey;

    public PatraFeignException(PlatformError error, String methodKey) {
        super(error);
        this.methodKey = methodKey;
    }

    public String methodKey() {
        return methodKey;
    }

}
