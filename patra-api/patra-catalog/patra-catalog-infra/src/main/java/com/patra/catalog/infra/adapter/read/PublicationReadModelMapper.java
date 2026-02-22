package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.AbstractInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.IdentifierInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo.MeshQualifierInfo;
import com.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import com.patra.catalog.infra.persistence.entity.PublicationIdentifierEntity;
import com.patra.catalog.infra.persistence.entity.PublicationMeshQualifierEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 文献出版物读模型 MapStruct 映射器。
///
/// 负责子实体到嵌套 ReadModel record 的简单映射。
/// 复杂的组装逻辑（如跨表 JOIN、venueName 补充）由 [PublicationReadAdapter] 处理。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicationReadModelMapper {

  /// 摘要实体 → 摘要信息。
  AbstractInfo toAbstractInfo(PublicationAbstractEntity entity);

  /// 标识符实体 → 标识符信息（枚举 type 自动转 String）。
  @Mapping(target = "type", expression = "java(entity.getType().name())")
  IdentifierInfo toIdentifierInfo(PublicationIdentifierEntity entity);

  /// MeSH 限定词实体 → 限定词信息。
  MeshQualifierInfo toMeshQualifierInfo(PublicationMeshQualifierEntity entity);
}
