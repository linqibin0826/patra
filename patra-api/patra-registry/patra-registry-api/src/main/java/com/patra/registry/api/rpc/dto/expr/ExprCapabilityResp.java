package com.patra.registry.api.rpc.dto.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO capturing capability metadata for expression fields.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>provenanceId - internal provenance identifier owning the capability</li>
 *   <li>operationType - operation discriminator associated with the capability</li>
 *   <li>fieldKey - expression field key the capability belongs to</li>
 *   <li>opsJson - serialized allowed operations for the field</li>
 *   <li>negatableOpsJson - serialized operations supporting negation</li>
 *   <li>supportsNot - whether logical NOT is supported for the field</li>
 *   <li>termMatchesJson - serialized match operators for term-based searches</li>
 *   <li>termCaseSensitiveAllowed - whether term operations may be case sensitive</li>
 *   <li>termAllowBlank - whether blank terms are permitted</li>
 *   <li>termMinLength - minimum allowed length for term input</li>
 *   <li>termMaxLength - maximum allowed length for term input</li>
 *   <li>termPattern - optional regex enforcing term formatting</li>
 *   <li>inMaxSize - maximum size for IN clauses</li>
 *   <li>inCaseSensitiveAllowed - whether IN values may be case sensitive</li>
 *   <li>rangeKindCode - range evaluation strategy discriminator</li>
 *   <li>rangeAllowOpenStart - whether open start ranges are allowed</li>
 *   <li>rangeAllowOpenEnd - whether open end ranges are allowed</li>
 *   <li>rangeAllowClosedAtInfinity - whether infinity bounds may be closed</li>
 *   <li>dateMin - minimum supported date value</li>
 *   <li>dateMax - maximum supported date value</li>
 *   <li>datetimeMin - minimum supported timestamp value</li>
 *   <li>datetimeMax - maximum supported timestamp value</li>
 *   <li>numberMin - minimum numeric value</li>
 *   <li>numberMax - maximum numeric value</li>
 *   <li>existsSupported - whether EXISTS operator is supported</li>
 *   <li>tokenKindsJson - serialized token kinds for tokenized queries</li>
 *   <li>tokenValuePattern - optional regex for token values</li>
 *   <li>effectiveFrom - timestamp from which the capability becomes effective</li>
 *   <li>effectiveTo - timestamp until which the capability remains effective</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCapabilityResp(
        Long provenanceId,
        String operationType,
        String fieldKey,
        String opsJson,
        String negatableOpsJson,
        boolean supportsNot,
        String termMatchesJson,
        boolean termCaseSensitiveAllowed,
        boolean termAllowBlank,
        int termMinLength,
        int termMaxLength,
        String termPattern,
        int inMaxSize,
        boolean inCaseSensitiveAllowed,
        String rangeKindCode,
        boolean rangeAllowOpenStart,
        boolean rangeAllowOpenEnd,
        boolean rangeAllowClosedAtInfinity,
        LocalDate dateMin,
        LocalDate dateMax,
        Instant datetimeMin,
        Instant datetimeMax,
        BigDecimal numberMin,
        BigDecimal numberMax,
        boolean existsSupported,
        String tokenKindsJson,
        String tokenValuePattern,
        Instant effectiveFrom,
        Instant effectiveTo
) {
}
