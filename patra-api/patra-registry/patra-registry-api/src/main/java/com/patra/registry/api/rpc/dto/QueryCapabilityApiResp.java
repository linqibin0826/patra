package com.patra.registry.api.rpc.dto;

import com.patra.common.enums.ProvenanceCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询能力对外 DTO（协议层）。
 * 不暴露内部技术ID，仅面向字段维度能力描述。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record QueryCapabilityApiResp(
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
) {
}
