package dev.patra.registry.infra.dictionary.persistence.mapper;

import dev.patra.registry.domain.dictionary.model.vo.ReferenceStandard;
import dev.patra.registry.infra.dictionary.persistence.entity.ReferenceStandardEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 来源标准 JPA 实体转换器。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReferenceStandardJpaMapper {

  /// 转换来源标准实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 来源标准领域对象
  @Mapping(target = "canonical", expression = "java(Boolean.TRUE.equals(entity.getCanonical()))")
  @Mapping(target = "enabled", expression = "java(Boolean.TRUE.equals(entity.getEnabled()))")
  ReferenceStandard toDomain(ReferenceStandardEntity entity);
}
