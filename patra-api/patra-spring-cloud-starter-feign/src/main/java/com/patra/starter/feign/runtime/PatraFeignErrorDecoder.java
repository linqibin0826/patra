package com.patra.starter.feign.runtime;

import com.patra.common.error.core.ErrorSpec;
import com.patra.common.error.core.PlatformError;
import com.patra.common.error.enums.COMErrors;
import com.patra.starter.core.error.codec.PlatformErrorCodec;
import com.patra.starter.core.error.runtime.PlatformErrorFactory;
import com.patra.starter.feign.error.PatraFeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

/**
 * Feign 错误解码器：将非 2xx 响应解析为 PlatformError，并抛出 PatraFeignException。
 */
@Slf4j
@RequiredArgsConstructor
public class PatraFeignErrorDecoder implements ErrorDecoder {

    private final PlatformErrorCodec codec;
    private final PatraFeignProperties props;

    @Override
    public PatraFeignException decode(String methodKey, Response response) {
        int httpStatus = response.status();
        byte[] bodyBytes = readBody(response, props.getMaxErrorBodySize());

        PlatformError error = null;
        // 1) 解析 problem+json
        try { error = codec.fromBytes(bodyBytes); } catch (Exception e) {
            log.warn("Failed to decode remote problem+json response: {}", e.getMessage());
        }

        // 2) 回退
        if (error == null) {
            error = mapStatusToDefaultCode(httpStatus);
        }
        if (error.status() == 0) {
            error = error.withStatus(ErrorSpec.recommendedHttpStatus(error.code().category()));
        }
        return new PatraFeignException(error, methodKey);
    }


    private byte[] readBody(Response response, int maxBytes) {
        if (response.body() == null) return new byte[0];
        try (InputStream in = response.body().asInputStream()) {
            return in.readNBytes(Math.max(0, maxBytes));
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static PlatformError mapStatusToDefaultCode(int status) {
        return switch (status) {
            case 401 -> PlatformErrorFactory.of(COMErrors.UNAUTHORIZED).build();
            case 403 -> PlatformErrorFactory.of(COMErrors.FORBIDDEN).build();
            case 404 -> PlatformErrorFactory.of(COMErrors.RESOURCE_NOT_FOUND).build();
            case 409 -> PlatformErrorFactory.of(COMErrors.VERSION_CONFLICT).build();
            case 422 -> PlatformErrorFactory.of(COMErrors.VALIDATION_FAILED).build();
            case 429 -> PlatformErrorFactory.of(COMErrors.RATE_LIMITED).build();
            case 502, 503 -> PlatformErrorFactory.of(COMErrors.CIRCUIT_BREAKER_OPEN).build();
            case 504 -> PlatformErrorFactory.of(COMErrors.CONNECT_TIMEOUT).build();
            default -> PlatformErrorFactory.of(COMErrors.UNEXPECTED_SERVER_ERROR).build();
        };
    }
}
