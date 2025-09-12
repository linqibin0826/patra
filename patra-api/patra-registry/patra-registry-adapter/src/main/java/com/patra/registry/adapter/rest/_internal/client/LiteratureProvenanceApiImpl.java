package com.patra.registry.adapter.rest._internal.client;

import cn.hutool.core.lang.Assert;
import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.adapter.rest._internal.converter.LiteratureProvenanceApiConverter;
import com.patra.registry.api.rpc.contract.LiteratureProvenanceHttpApi;
import com.patra.registry.api.rpc.dto.LiteratureProvenanceConfigApiResp;
import com.patra.registry.api.rpc.dto.QueryCapabilityApiResp;
import com.patra.registry.api.rpc.dto.ApiParamMappingApiResp;
import com.patra.registry.api.rpc.dto.QueryRenderRuleApiResp;
import com.patra.registry.api.rpc.dto.ProvenanceExprConfigSnapshotApiResp;
import com.patra.registry.api.rpc.dto.PlatformFieldDictApiResp;
import com.patra.registry.app.usecase.LiteratureProvenanceQueryUseCase;
import com.patra.registry.app.usecase.PlatformFieldDictQueryUseCase;
import com.patra.registry.adapter.rest._internal.converter.PlatformFieldDictApiConverter;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 文献数据源内部 HTTP 提供方实现。
 *
 * <p>职责: 参数接收、日志与模型转换，业务查询通过 app/use case 下沉至端口实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LiteratureProvenanceApiImpl implements LiteratureProvenanceHttpApi {
    private final LiteratureProvenanceQueryUseCase queryUseCase;
    private final LiteratureProvenanceApiConverter apiConverter;
    private final PlatformFieldDictQueryUseCase fieldDictUseCase;
    private final PlatformFieldDictApiConverter fieldDictConverter;

    @Override
    public LiteratureProvenanceConfigApiResp getConfigByCode(ProvenanceCode provenanceCode) {
        log.info("[LiteratureProvenance] GetConfig, code={}", provenanceCode);
        var view = queryUseCase.getConfigView(provenanceCode);
        return apiConverter.toConfigApiResp(view);
    }

    @Override
    public java.util.List<QueryCapabilityApiResp> getQueryCapabilitiesByCode(ProvenanceCode provenanceCode) {
        log.info("[LiteratureProvenance] GetQueryCapabilities, code={}", provenanceCode);
        var views = queryUseCase.getQueryCapabilities(provenanceCode);
        return apiConverter.toQueryCapabilityApiRespList(views);
    }

    @Override
    public java.util.List<ApiParamMappingApiResp> getApiParamMappingsByCode(ProvenanceCode provenanceCode) {
        log.info("[LiteratureProvenance] GetApiParamMappings, code={}", provenanceCode);
        var views = queryUseCase.getApiParamMappings(provenanceCode);
        return apiConverter.toApiParamMappingApiRespList(views);
    }

    @Override
    public java.util.List<QueryRenderRuleApiResp> getQueryRenderRulesByCode(ProvenanceCode provenanceCode) {
        log.info("[LiteratureProvenance] GetQueryRenderRules, code={}", provenanceCode);
        var views = queryUseCase.getQueryRenderRules(provenanceCode);
        return apiConverter.ruleViews2ApiList(views);
    }

    /**
     * 一次性返回指定数据源 + operation 的表达式配置快照。
     *
     * <p>注意：apiParams 通过用例在 DB 端按 operation 过滤，避免全量内存过滤。
     *
     * @param provenanceCode 数据源代码
     * @param operation 操作：search/fetch/lookup 等
     * @return 快照聚合 DTO
     */
    @Override
    public ProvenanceExprConfigSnapshotApiResp getExprConfigSnapshot(ProvenanceCode provenanceCode, String operation) {
        log.info("[LiteratureProvenance] GetExprConfigSnapshot, code={}, operation={} - start", provenanceCode, operation);
        Assert.notNull(provenanceCode, "provenanceCode must not be null");
        Assert.notBlank(operation, "operation must not be blank");

        var configView = queryUseCase.getConfigView(provenanceCode);
        var capabilityViews = queryUseCase.getQueryCapabilities(provenanceCode);
        var renderRuleViews = queryUseCase.getQueryRenderRules(provenanceCode);
        // 按 operation 过滤（DB 端）
        var apiParamViews = (operation == null || operation.isBlank())
                ? List.<ApiParamMappingView>of()
                : queryUseCase.getApiParamMappingsByOperation(provenanceCode, operation);
        List<com.patra.registry.contract.query.view.PlatformFieldDictView> fieldDictViews = fieldDictUseCase.listAll();

        List<QueryCapabilityApiResp> capabilities = apiConverter.toQueryCapabilityApiRespList(capabilityViews);
        List<QueryRenderRuleApiResp> renderRules = apiConverter.ruleViews2ApiList(renderRuleViews);
        List<ApiParamMappingApiResp> apiParams = apiConverter.toApiParamMappingApiRespList(apiParamViews);
        List<PlatformFieldDictApiResp> fieldDict = fieldDictConverter.toApiRespList(fieldDictViews);

        long version = 0L; // 暂无规则源版本，默认 0（后续可由端口提供组合版本/更新时间）
        Instant updatedAt = null; // 暂无更新时间

        log.info("[LiteratureProvenance] GetExprConfigSnapshot, code={}, operation={} - done. summary: capabilities={}, renderRules={}, apiParams={}, fieldDict={}",
                provenanceCode, operation,
                capabilities == null ? 0 : capabilities.size(),
                renderRules == null ? 0 : renderRules.size(),
                apiParams == null ? 0 : apiParams.size(),
                fieldDict == null ? 0 : fieldDict.size());

        return new ProvenanceExprConfigSnapshotApiResp(
                configView.provenanceId(),
                configView.provenanceCode() == null ? null : configView.provenanceCode().getCode(),
                operation,
                version,
                updatedAt,
                fieldDict,
                capabilities,
                renderRules,
                apiParams
        );
    }
}
