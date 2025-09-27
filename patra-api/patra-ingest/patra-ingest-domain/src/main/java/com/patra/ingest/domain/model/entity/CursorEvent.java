package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.CursorLineage;
import java.time.Instant;
import java.math.BigDecimal;
import lombok.Getter;

/** 游标推进事件(append-only)。 */
@SuppressWarnings("unused")
@Getter
public class CursorEvent {
    private final Long id;
    private final String provenanceCode;
    private final String operationCode;
    private final String cursorKey;
    private final String namespaceScopeCode;
    private final String namespaceKey;
    private final CursorType cursorType;
    private final String prevValue;
    private final String newValue;
    private final Instant windowFrom;
    private final Instant windowTo;
    private final CursorDirection direction;
    private final String idempotentKey;
    private final String observedMaxValue;
    private final Instant prevInstant;
    private final Instant newInstant;
    private final BigDecimal prevNumeric;
    private final BigDecimal newNumeric;
    private final CursorLineage lineage;
    private final String exprHash;

    private CursorEvent(Long id,
                        String provenanceCode,
                        String operationCode,
                        String cursorKey,
                        String namespaceScopeCode,
                        String namespaceKey,
                        CursorType cursorType,
                        String prevValue,
                        String newValue,
                        Instant windowFrom,
                        Instant windowTo,
                        CursorDirection direction,
                        String idempotentKey,
                        String observedMaxValue,
                        Instant prevInstant,
                        Instant newInstant,
                        BigDecimal prevNumeric,
                        BigDecimal newNumeric,
                        CursorLineage lineage,
                        String exprHash) {
        this.id = id;
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
        this.cursorKey = cursorKey;
        this.namespaceScopeCode = namespaceScopeCode;
        this.namespaceKey = namespaceKey;
        this.cursorType = cursorType;
        this.prevValue = prevValue;
        this.newValue = newValue;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.direction = direction;
        this.idempotentKey = idempotentKey;
        this.observedMaxValue = observedMaxValue;
        this.prevInstant = prevInstant;
        this.newInstant = newInstant;
        this.prevNumeric = prevNumeric;
        this.newNumeric = newNumeric;
        this.lineage = lineage == null ? CursorLineage.empty() : lineage;
        this.exprHash = exprHash;
    }

    public static CursorEvent restore(Long id,
                                      String provenanceCode,
                                      String operationCode,
                                      String cursorKey,
                                      String namespaceScopeCode,
                                      String namespaceKey,
                                      CursorType cursorType,
                                      String prevValue,
                                      String newValue,
                                      Instant windowFrom,
                                      Instant windowTo,
                                      CursorDirection direction,
                                      String idempotentKey,
                                      String observedMaxValue,
                                      Instant prevInstant,
                                      Instant newInstant,
                                      BigDecimal prevNumeric,
                                      BigDecimal newNumeric,
                                      CursorLineage lineage,
                                      String exprHash) {
        return new CursorEvent(id,
                provenanceCode,
                operationCode,
                cursorKey,
                namespaceScopeCode,
                namespaceKey,
                cursorType,
                prevValue,
                newValue,
                windowFrom,
                windowTo,
                direction,
                idempotentKey,
                observedMaxValue,
                prevInstant,
                newInstant,
                prevNumeric,
                newNumeric,
                lineage,
                exprHash);
    }
}
