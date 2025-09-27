package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.CursorLineage;
import com.patra.ingest.domain.model.vo.CursorValue;
import com.patra.ingest.domain.model.vo.CursorWatermark;
import lombok.Getter;

/** 当前游标。 */
@SuppressWarnings("unused")
@Getter
public class Cursor {
    private final Long id;
    private final String provenanceCode;
    private final String operationCode;
    private final String cursorKey;
    private final NamespaceScope namespaceScope;
    private final String namespaceKey;
    private final CursorType cursorType;
    private CursorValue value;
    private CursorWatermark watermark;
    private CursorLineage lineage;
    private String exprHash;

    private Cursor(Long id,
                   String provenanceCode,
                   String operationCode,
                   String cursorKey,
                   NamespaceScope namespaceScope,
                   String namespaceKey,
                   CursorType cursorType,
                   CursorValue value,
                   CursorWatermark watermark,
                   CursorLineage lineage,
                   String exprHash) {
        this.id = id;
        this.provenanceCode = provenanceCode;
        this.operationCode = operationCode;
        this.cursorKey = cursorKey;
        this.namespaceScope = namespaceScope;
        this.namespaceKey = namespaceKey;
        this.cursorType = cursorType;
        this.value = value;
        this.watermark = watermark == null ? CursorWatermark.empty() : watermark;
        this.lineage = lineage == null ? CursorLineage.empty() : lineage;
        this.exprHash = exprHash;
    }

    public static Cursor restore(Long id,
                                 String provenanceCode,
                                 String operationCode,
                                 String cursorKey,
                                 NamespaceScope namespaceScope,
                                 String namespaceKey,
                                 CursorType cursorType,
                                 CursorValue value,
                                 CursorWatermark watermark,
                                 CursorLineage lineage,
                                 String exprHash) {
        return new Cursor(id, provenanceCode, operationCode, cursorKey, namespaceScope, namespaceKey, cursorType, value, watermark, lineage, exprHash);
    }

    public void advance(CursorValue newValue, CursorWatermark newWatermark, CursorLineage newLineage, String newExprHash) {
        if (newValue == null) {
            throw new IllegalArgumentException("游标值不能为空");
        }
        this.value = newValue;
        if (newWatermark != null) {
            this.watermark = newWatermark;
        }
        if (newLineage != null) {
            this.lineage = newLineage;
        }
        if (newExprHash != null && !newExprHash.isBlank()) {
            this.exprHash = newExprHash;
        }
    }

    public void updateObservedMax(String observedMax) {
        this.watermark = new CursorWatermark(observedMax, watermark.normalizedInstant(), watermark.normalizedNumeric());
    }
}
