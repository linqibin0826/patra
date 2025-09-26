package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import java.time.Instant;

/** 游标推进事件(append-only)。 */
@SuppressWarnings("unused")
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
    public CursorEvent(Long id,String provenanceCode,String operationCode,String cursorKey,String namespaceScopeCode,String namespaceKey,CursorType cursorType,String prevValue,String newValue,Instant windowFrom,Instant windowTo,CursorDirection direction,String idempotentKey){
        this.id=id;this.provenanceCode=provenanceCode;this.operationCode=operationCode;this.cursorKey=cursorKey;this.namespaceScopeCode=namespaceScopeCode;this.namespaceKey=namespaceKey;this.cursorType=cursorType;this.prevValue=prevValue;this.newValue=newValue;this.windowFrom=windowFrom;this.windowTo=windowTo;this.direction=direction;this.idempotentKey=idempotentKey;}
}
