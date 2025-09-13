package com.patra.registry.contract.query.view;

import com.patra.common.enums.ProvenanceCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询能力的读侧视图（只读投影）。
 * 与 API DTO 基本等价，但允许内部按需扩展。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record QueryCapabilityView(
        ProvenanceCode provenanceCode,
        String fieldKey,
        List<String> ops,
        List<String> negatableOps,
        Boolean supportsNot,
        List<String> termMatches,
        Boolean termCaseSensitiveAllowed,
        Boolean termAllowBlank,
        Integer termMinLen,
        Integer termMaxLen,
        String termPattern,
        Integer inMaxSize,
        Boolean inCaseSensitiveAllowed,
        String rangeKind,
        Boolean rangeAllowOpenStart,
        Boolean rangeAllowOpenEnd,
        Boolean rangeAllowClosedAtInfty,
        LocalDate dateMin,
        LocalDate dateMax,
        Instant datetimeMin,
        Instant datetimeMax,
        BigDecimal numberMin,
        BigDecimal numberMax,
        Boolean existsSupported,
        List<String> tokenKinds,
        String tokenValuePattern
) {}
