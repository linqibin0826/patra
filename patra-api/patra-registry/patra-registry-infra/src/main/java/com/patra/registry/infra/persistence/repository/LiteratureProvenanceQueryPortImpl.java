package com.patra.registry.infra.persistence.repository;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.port.LiteratureProvenanceQueryPort;
import com.patra.registry.contract.query.view.LiteratureProvenanceConfigView;
import com.patra.registry.contract.query.view.ProvenanceSummaryView;
import com.patra.registry.contract.query.view.QueryCapabilityView;
import com.patra.registry.contract.query.view.ApiParamMappingView;
import com.patra.registry.contract.query.view.QueryRenderRuleView;
import com.patra.registry.infra.mapstruct.ApiParamMappingConverter;
import com.patra.registry.infra.mapstruct.LiteratureProvenanceConverter;
import com.patra.registry.infra.mapstruct.QueryCapabilityConverter;
import com.patra.registry.infra.mapstruct.QueryRenderRuleConverter;
import com.patra.registry.infra.persistence.entity.LiteratureProvenanceDO;
import com.patra.registry.infra.persistence.entity.SourceQueryCapabilityDO;
import com.patra.registry.infra.persistence.entity.SourceApiParamMappingDO;
import com.patra.registry.infra.persistence.entity.SourceQueryRenderRuleDO;
import com.patra.registry.infra.persistence.mapper.LiteratureProvenanceMapper;
import com.patra.registry.infra.persistence.mapper.SourceQueryCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.SourceApiParamMappingMapper;
import com.patra.registry.infra.persistence.mapper.SourceQueryRenderRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LiteratureProvenanceQueryPortImpl implements LiteratureProvenanceQueryPort {

    private final LiteratureProvenanceMapper provenanceMapper;
    private final LiteratureProvenanceConverter converter;
    private final SourceQueryCapabilityMapper capabilityMapper;
    private final QueryCapabilityConverter capabilityConverter;
    private final SourceApiParamMappingMapper apiParamMappingMapper;
    private final ApiParamMappingConverter apiParamMappingConverter;
    private final SourceQueryRenderRuleMapper queryRenderRuleMapper;
    private final QueryRenderRuleConverter queryRenderRuleConverter;


    @Override
    public List<ProvenanceSummaryView> findAll() {
        List<LiteratureProvenanceDO> provenances = provenanceMapper.selectProvSummaryAll();
        return provenances.stream().map(converter::toSummary).toList();
    }

    @Override
    public LiteratureProvenanceConfigView getConfigByProvenanceCode(ProvenanceCode provenanceCode) {
        return provenanceMapper.selectConfigByCode(provenanceCode.getCode());
    }

    @Override
    public List<QueryCapabilityView> getQueryCapabilitiesByProvenanceCode(ProvenanceCode provenanceCode) {
        // 先查出 provenanceId
        LambdaQueryWrapper<LiteratureProvenanceDO> provQ = new LambdaQueryWrapper<>();
        provQ.select(LiteratureProvenanceDO::getId)
                .eq(LiteratureProvenanceDO::getCode, provenanceCode);
        LiteratureProvenanceDO prov = provenanceMapper.selectOne(provQ);
        if (prov == null) {
            return List.of();
        }
        // 根据 provenanceId 查询 capability DO 列表
        LambdaQueryWrapper<SourceQueryCapabilityDO> capQ = new LambdaQueryWrapper<>();
        capQ.eq(SourceQueryCapabilityDO::getLiteratureProvenanceId, prov.getId());
        List<SourceQueryCapabilityDO> dos = capabilityMapper.selectList(capQ);
        return capabilityConverter.toViewList(dos, provenanceCode);
    }

    @Override
    public List<ApiParamMappingView> getApiParamMappingsByProvenanceCode(ProvenanceCode provenanceCode) {
    // 查 provenanceId
    var prov = provenanceMapper.selectOne(new LambdaQueryWrapper<LiteratureProvenanceDO>()
        .select(LiteratureProvenanceDO::getId)
        .eq(LiteratureProvenanceDO::getCode, provenanceCode));
    if (prov == null) return java.util.List.of();
    var list = apiParamMappingMapper.selectList(new LambdaQueryWrapper<SourceApiParamMappingDO>()
        .eq(SourceApiParamMappingDO::getLiteratureProvenanceId, prov.getId()));
        return list.stream().map(apiParamMappingConverter::mapApiParam).toList();
    }

    @Override
    public List<ApiParamMappingView> getApiParamMappingsByProvenanceCodeAndOperation(ProvenanceCode provenanceCode, String operation) {
        // 根据 code → id
        var prov = provenanceMapper.selectOne(new LambdaQueryWrapper<LiteratureProvenanceDO>()
                .select(LiteratureProvenanceDO::getId)
                .eq(LiteratureProvenanceDO::getCode, provenanceCode));
        if (prov == null) return java.util.List.of();
        // 直接在 DB 端按 operation 过滤，避免全量入内存
        var list = apiParamMappingMapper.selectList(new LambdaQueryWrapper<SourceApiParamMappingDO>()
                .eq(SourceApiParamMappingDO::getLiteratureProvenanceId, prov.getId())
                .eq(SourceApiParamMappingDO::getOperation, operation));
        return list.stream().map(apiParamMappingConverter::mapApiParam).toList();
    }

    @Override
    public List<QueryRenderRuleView> getQueryRenderRulesByProvenanceCode(ProvenanceCode provenanceCode) {
    // 查 provenanceId
    var prov = provenanceMapper.selectOne(new LambdaQueryWrapper<LiteratureProvenanceDO>()
        .select(LiteratureProvenanceDO::getId)
        .eq(LiteratureProvenanceDO::getCode, provenanceCode));
    if (prov == null) return java.util.List.of();
    var list = queryRenderRuleMapper.selectList(new LambdaQueryWrapper<SourceQueryRenderRuleDO>()
        .eq(SourceQueryRenderRuleDO::getLiteratureProvenanceId, prov.getId()));
    return queryRenderRuleConverter.toViewList(list);
    }
}
