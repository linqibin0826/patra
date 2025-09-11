package com.patra.starter.web.advice;

import com.patra.common.error.core.PlatformError;
import com.patra.common.error.enums.COMErrors;
import com.patra.starter.core.error.codec.PlatformErrorCodec;
import com.patra.starter.core.error.codec.ProblemJsonConstant;
import com.patra.starter.core.error.runtime.PlatformErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class PatraGlobalExceptionHandler {

    private final PlatformErrorCodec codec; // 建议在自动装配里注入 JacksonPlatformErrorCodec

    @Value("${spring.application.name:unknown-service}")
    private String service;

    /**
     * 兜底：IllegalArgumentException → COM-C0101
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        PlatformError pe = PlatformErrorFactory.of(COMErrors.MISSING_OR_INVALID_PARAMETER)
                .detail(safeMsg(ex))
                .service(service)
                .instance(req.getRequestURI())
                .build();
        log.warn("IllegalArgument: {}", pe.detail());
        return write(pe);
    }

    /**
     * Bean Validation：MethodArgumentNotValidException → COM-C0201
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var errors = new ArrayList<Map<String, Object>>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, Object> item = new HashMap<>();
            item.put("field", fe.getField());
            item.put("message", fe.getDefaultMessage());
            errors.add(item);
        });
        PlatformError pe = PlatformErrorFactory.of(COMErrors.VALIDATION_FAILED)
                .detail("Request validation failed")
                .param("errors", errors)
                .service(service)
                .instance(req.getRequestURI())
                .build();

        return write(pe);
    }

    /**
     * 统一写出为 application/problem+json
     */
    private ResponseEntity<?> write(PlatformError pe) {
        return ResponseEntity.status(pe.status())
                .contentType(MediaType.valueOf(ProblemJsonConstant.MEDIA_TYPE))
                .body(codec.toProblemMap(pe));
    }

    private String safeMsg(Throwable ex) {
        // 可做敏感信息清洗/裁剪
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) return "Invalid request";
        return msg.length() > 400 ? msg.substring(0, 400) + "…" : msg;
    }
}
