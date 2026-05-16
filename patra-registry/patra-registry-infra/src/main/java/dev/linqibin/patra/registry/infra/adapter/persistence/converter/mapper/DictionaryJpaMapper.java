package dev.linqibin.patra.registry.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import dev.linqibin.patra.registry.domain.model.vo.dictionary.DictionaryType;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictItemEntity;
import dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary.SysDictTypeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 字典 JPA 实体转换器,负责将 JPA 实体转换为领域值对象。
///
/// 仅用于 CQRS 读侧的字典解析场景。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DictionaryJpaMapper {

  /// 转换字典类型实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 字典类型领域对象
  DictionaryType toDomain(SysDictTypeEntity entity);

  /// 转换字典项实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 字典项领域对象
  @Mapping(target = "enabled", expression = "java(Boolean.TRUE.equals(entity.getEnabled()))")
  DictionaryItem toDomain(SysDictItemEntity entity);
}
