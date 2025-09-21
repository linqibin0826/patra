package com.patra.ingest.contract.query.view;

import java.time.Instant;

/** 计划只读投影。 */
public record PlanQuery(Long id,String planKey,String provenanceCode,String endpointName,String operationCode,Instant windowFrom,Instant windowTo,String statusCode) {}
