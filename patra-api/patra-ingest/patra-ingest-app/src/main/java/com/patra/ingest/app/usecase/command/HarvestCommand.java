package com.patra.ingest.app.usecase.command;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;

/** 常规增量：从上次采集时间或指定起点 */
public record HarvestCommand(
        ProvenanceCode provenance,
//        Sorting sorting,
        Expr expr,
        boolean dryRun,
        String traceId
) implements IngestCommand { }
