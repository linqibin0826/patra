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
import com.patra.registry.contract.query.view.PlatformFieldDictView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * @param operation      操作：search/fetch/lookup 等
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
        List<PlatformFieldDictView> fieldDictViews = fieldDictUseCase.listAll();

        List<QueryCapabilityApiResp> capabilities = Optional.ofNullable(apiConverter.toQueryCapabilityApiRespList(capabilityViews)).orElseGet(List::of);
        List<QueryRenderRuleApiResp> renderRules = Optional.ofNullable(apiConverter.ruleViews2ApiList(renderRuleViews)).orElseGet(List::of);
        List<ApiParamMappingApiResp> apiParams = Optional.ofNullable(apiConverter.toApiParamMappingApiRespList(apiParamViews)).orElseGet(List::of);
        List<PlatformFieldDictApiResp> fieldDict = Optional.ofNullable(fieldDictConverter.toApiRespList(fieldDictViews)).orElseGet(List::of);

        // 贴近下游结构：
        // - renderRules: 按 priority 降序，其次 fieldKey/op 升序，null 安全
        renderRules = renderRules.stream()
                .sorted(Comparator
                        .comparing((QueryRenderRuleApiResp r) -> Optional.ofNullable(r.priority()).orElse(Integer.MIN_VALUE)).reversed()
                        .thenComparing(r -> Optional.ofNullable(r.fieldKey()).orElse(""))
                        .thenComparing(r -> Optional.ofNullable(r.op()).orElse("")))
                .collect(Collectors.toList());

        // - fieldDict: 以 fieldKey 去重并按 key 升序稳定输出，然后转 Map
        LinkedHashMap<String, PlatformFieldDictApiResp> fieldDictMap = fieldDict.stream()
                .filter(it -> it.fieldKey() != null)
                .sorted(Comparator.comparing(PlatformFieldDictApiResp::fieldKey))
                .collect(Collectors.toMap(PlatformFieldDictApiResp::fieldKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        // - capabilities: 以 fieldKey 去重（若重复则保留第一次），按 fieldKey 升序输出，然后转 Map
        LinkedHashMap<String, QueryCapabilityApiResp> capabilitiesMap = capabilities.stream()
                .filter(it -> it.fieldKey() != null)
                .sorted(Comparator.comparing(QueryCapabilityApiResp::fieldKey))
                .collect(Collectors.toMap(QueryCapabilityApiResp::fieldKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        // - apiParams: 以 stdKey 去重（保留第一次），按 stdKey 升序输出，然后转 Map
        LinkedHashMap<String, ApiParamMappingApiResp> apiParamsMap = apiParams.stream()
                .filter(it -> it.stdKey() != null)
                .sorted(Comparator.comparing(ApiParamMappingApiResp::stdKey))
                .collect(Collectors.toMap(ApiParamMappingApiResp::stdKey, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        long version = 0L; // 暂无规则源版本，默认 0（后续可由端口提供组合版本/更新时间）
        Instant updatedAt = null; // 暂无更新时间

        log.info("[LiteratureProvenance] GetExprConfigSnapshot, code={}, operation={} - done. summary: capabilities={}, renderRules={}, apiParams={}, fieldDict={}",
                provenanceCode, operation,
                capabilitiesMap.size(),
                renderRules.size(),
                apiParamsMap.size(),
                fieldDictMap.size());

        return new ProvenanceExprConfigSnapshotApiResp(
                configView.provenanceId(),
                configView.provenanceCode() == null ? null : configView.provenanceCode().getCode(),
                operation,
                version,
                updatedAt,
                fieldDictMap,
                capabilitiesMap,
                renderRules,
                apiParamsMap
        );
    }
}
