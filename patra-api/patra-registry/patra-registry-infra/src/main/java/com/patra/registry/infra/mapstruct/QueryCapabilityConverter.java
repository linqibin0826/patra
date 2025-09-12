package com.patra.registry.infra.mapstruct;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.contract.query.view.QueryCapabilityView;
import com.patra.registry.domain.model.enums.RangeKind;
import com.patra.registry.infra.persistence.entity.SourceQueryCapabilityDO;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 将查询能力 DO 转为读侧视图的转换器。
 *
 * <p>负责：
 * - JsonNode → List<String> 的容错转换
 * - RangeKind → String 的安全映射
 * - 注入 provenanceCode（DO 不含）
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface QueryCapabilityConverter {

    @Mapping(target = "provenanceCode", source = "provenanceCode")
    @Mapping(target = "ops", expression = "java(toStringList(src.getOps()))")
    @Mapping(target = "negatableOps", expression = "java(toStringList(src.getNegatableOps()))")
    @Mapping(target = "termMatches", expression = "java(toStringList(src.getTermMatches()))")
    @Mapping(target = "rangeKind", expression = "java(toRangeKindString(src.getRangeKind()))")
    @Mapping(target = "tokenKinds", expression = "java(toStringList(src.getTokenKinds()))")
    @Named("mapCapability")
    QueryCapabilityView mapCapability(SourceQueryCapabilityDO src, ProvenanceCode provenanceCode);

    default List<QueryCapabilityView> toViewList(List<SourceQueryCapabilityDO> src, ProvenanceCode code) {
        if (src == null || src.isEmpty()) return java.util.List.of();
        List<QueryCapabilityView> list = new ArrayList<>(src.size());
        for (SourceQueryCapabilityDO it : src) {
            list.add(mapCapability(it, code));
        }
        return list;
    }

    default List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) return java.util.List.of();
        if (!node.isArray()) return java.util.List.of(node.asText());
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toList());
    }

    default String toRangeKindString(RangeKind kind) {
        return kind == null ? null : kind.name();
    }
}
