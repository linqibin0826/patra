package com.patra.registry.infra.mapstruct;

import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;
import com.patra.registry.infra.persistence.entity.expr.RegExprFieldDictDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Expr 相关实体到领域对象的转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExprEntityConverter {

    @Mapping(target = "exposable", expression = "java(Boolean.TRUE.equals(entity.getExposable()))")
    @Mapping(target = "dateField", expression = "java(Boolean.TRUE.equals(entity.getDateField()))")
    ExprField toDomain(RegExprFieldDictDO entity);

    @Mapping(target = "notesJson", source = "notes")
    ApiParamMapping toDomain(RegProvApiParamMapDO entity);

    @Mapping(target = "opsJson", source = "ops")
    @Mapping(target = "negatableOpsJson", source = "negatableOps")
    @Mapping(target = "supportsNot", expression = "java(Boolean.TRUE.equals(entity.getSupportsNot()))")
    @Mapping(target = "termMatchesJson", source = "termMatches")
    @Mapping(target = "termCaseSensitiveAllowed", expression = "java(Boolean.TRUE.equals(entity.getTermCaseSensitiveAllowed()))")
    @Mapping(target = "termAllowBlank", expression = "java(Boolean.TRUE.equals(entity.getTermAllowBlank()))")
    @Mapping(target = "inCaseSensitiveAllowed", expression = "java(Boolean.TRUE.equals(entity.getInCaseSensitiveAllowed()))")
    @Mapping(target = "rangeAllowOpenStart", expression = "java(Boolean.TRUE.equals(entity.getRangeAllowOpenStart()))")
    @Mapping(target = "rangeAllowOpenEnd", expression = "java(Boolean.TRUE.equals(entity.getRangeAllowOpenEnd()))")
    @Mapping(target = "rangeAllowClosedAtInfinity", expression = "java(Boolean.TRUE.equals(entity.getRangeAllowClosedAtInfty()))")
    @Mapping(target = "existsSupported", expression = "java(Boolean.TRUE.equals(entity.getExistsSupported()))")
    @Mapping(target = "tokenKindsJson", source = "tokenKinds")
    ExprCapability toDomain(RegProvExprCapabilityDO entity);

    @Mapping(target = "negated", expression = "java(entity.getNegated())")
    @Mapping(target = "wrapGroup", expression = "java(Boolean.TRUE.equals(entity.getWrapGroup()))")
    @Mapping(target = "paramsJson", source = "params")
    @Mapping(target = "functionCode", source = "fnCode")
    ExprRenderRule toDomain(RegProvExprRenderRuleDO entity);
}
