package com.patra.registry.api.rpc.endpoint;

import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/**
 * Expr 子域内部 API 契约。
 */
public interface ExprEndpoint {

    String BASE_PATH = "/_internal/expr";

    /**
     * 加载聚合快照。
     */
    @GetMapping(BASE_PATH + "/snapshot")
    ExprSnapshotResp getSnapshot(@RequestParam("provenanceCode") String provenanceCode,
                                 @RequestParam(value = "taskType", required = false) String taskType,
                                 @RequestParam("operationCode") String operationCode,
                                 @RequestParam(value = "at", required = false) Instant at);
}
