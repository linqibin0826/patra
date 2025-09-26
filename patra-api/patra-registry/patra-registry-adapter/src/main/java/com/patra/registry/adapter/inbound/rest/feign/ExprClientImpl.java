package com.patra.registry.adapter.inbound.rest.feign;

import com.patra.registry.adapter.inbound.rest.feign.converter.ExprApiConverter;
import com.patra.registry.api.rpc.client.ExprClient;
import com.patra.registry.api.rpc.dto.expr.ExprSnapshotResp;
import com.patra.registry.app.service.ExprQueryAppService;
import com.patra.registry.domain.model.read.expr.ExprSnapshotQuery;
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
    private final ExprApiConverter converter;

    @Override
    public ExprSnapshotResp getSnapshot(String provenanceCode,
                                        String taskType,
                                        String operationCode,
                                        Instant at) {
        ExprSnapshotQuery snapshot = exprQueryAppService.loadSnapshot(provenanceCode, taskType, operationCode, at);
        return converter.toResp(snapshot);
    }
}
