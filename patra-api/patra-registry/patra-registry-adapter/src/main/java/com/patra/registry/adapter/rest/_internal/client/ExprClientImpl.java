package com.patra.registry.adapter.rest._internal.client;

import com.patra.registry.adapter.rest._internal.convertor.ExprApiConvertor;
import com.patra.registry.api.rpc.client.ExprClient;
import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import com.patra.registry.app.service.ExprQueryAppService;
import com.patra.registry.contract.query.view.expr.ExprSnapshotQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
/**
 * Expr 内部 API 实现。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExprClientImpl implements ExprClient {

    private final ExprQueryAppService exprQueryAppService;
    private final ExprApiConvertor convertor;

    @Override
    public ExprSnapshotResp getSnapshot(String provenanceCode,
                                        String taskType,
                                        String operationCode,
                                        Instant at) {
        ExprSnapshotQuery snapshot = exprQueryAppService.loadSnapshot(provenanceCode, taskType, operationCode, at);
        return convertor.toResp(snapshot);
    }
}
