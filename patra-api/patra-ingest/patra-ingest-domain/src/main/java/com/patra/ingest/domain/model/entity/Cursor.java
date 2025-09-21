package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.CursorValue;

/** 当前游标。 */
public class Cursor {
    private final Long id;
    private final String provenanceCode;
    private final String operationCode;
    private final String cursorKey;
    private final NamespaceScope namespaceScope;
    private final String namespaceKey;
    private final CursorType cursorType;
    private CursorValue value;
    private String exprHash;
    public Cursor(Long id,String provenanceCode,String operationCode,String cursorKey,NamespaceScope nsScope,String nsKey,CursorType type,CursorValue value,String exprHash){
        this.id=id;this.provenanceCode=provenanceCode;this.operationCode=operationCode;this.cursorKey=cursorKey;this.namespaceScope=nsScope;this.namespaceKey=nsKey;this.cursorType=type;this.value=value;this.exprHash=exprHash;}
    public void advance(CursorValue newValue,String exprHash){ this.value=newValue; this.exprHash=exprHash; }
}
