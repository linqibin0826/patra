package com.patra.ingest.app.usecase.command;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;

/** 采集命令：统一携带 Provenance、排序、Expr（系统统一AST） */
public sealed interface IngestCommand
        permits HarvestCommand {

    ProvenanceCode provenance();   // 全系统唯一标识的数据源
//    Sorting sorting();            // TODO 系统统一的排序规范
    Expr expr();                  // 系统统一的查询表达式（AST）
    boolean dryRun();             // 仅规划/渲染，不落库/不请求下游
    String traceId();             // 幂等/追踪
}
