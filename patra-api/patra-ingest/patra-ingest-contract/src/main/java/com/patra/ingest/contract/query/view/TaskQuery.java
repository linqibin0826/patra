package com.patra.ingest.contract.query.view;

import java.time.Instant;

/** 任务只读投影。 */
public record TaskQuery(Long id,Long planId,Long sliceId,String provenanceCode,String operationCode,String statusCode,Instant scheduledAt,Instant startedAt,Instant finishedAt) {}
