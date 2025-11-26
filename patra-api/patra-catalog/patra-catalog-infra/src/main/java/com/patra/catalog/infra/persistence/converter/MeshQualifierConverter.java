package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;
import org.springframework.stereotype.Component;

/// MeSH 限定词聚合根转换器。
///
/// **职责**：
///
/// - MeshQualifierAggregate ↔ MeshQualifierDO 转换
///   - 值对象(MeshUI)与基本类型(String)的转换
///   - 聚合根与DO实体的双向转换
///
/// @author linqibin
/// @since 0.1.0
@Component
public class MeshQualifierConverter {

  /// 将聚合根转换为数据库实体。
  ///
  /// @param aggregate 限定词聚合根
  /// @return 数据库实体
  public MeshQualifierDO toDataObject(MeshQualifierAggregate aggregate) {
    if (aggregate == null) {
      return null;
    }

    MeshQualifierDO dataObject = new MeshQualifierDO();
    dataObject.setId(aggregate.getId());
    dataObject.setUi(aggregate.getQualifierUi().ui());
    dataObject.setName(aggregate.getName());
    dataObject.setAbbreviation(aggregate.getAbbreviation());
    dataObject.setAnnotation(aggregate.getAnnotation());
    dataObject.setDateCreated(aggregate.getDateCreated());
    dataObject.setDateRevised(aggregate.getDateRevised());
    dataObject.setDateEstablished(aggregate.getDateEstablished());
    dataObject.setActiveStatus(aggregate.getActiveStatus());
    dataObject.setMeshVersion(aggregate.getMeshVersion());

    return dataObject;
  }

  /// 将数据库实体转换为聚合根。
  ///
  /// @param dataObject 数据库实体
  /// @return 限定词聚合根
  public MeshQualifierAggregate toDomain(MeshQualifierDO dataObject) {
    if (dataObject == null) {
      return null;
    }

    // 直接使用原始 UI 字符串，保留格式（支持 7 位和 10 位）
    MeshUI qualifierUi = MeshUI.of(dataObject.getUi());

    return MeshQualifierAggregate.restore(
        dataObject.getId(),
        qualifierUi,
        dataObject.getName(),
        dataObject.getAbbreviation(),
        dataObject.getAnnotation(),
        dataObject.getDateCreated(),
        dataObject.getDateRevised(),
        dataObject.getDateEstablished(),
        dataObject.getActiveStatus(),
        dataObject.getMeshVersion());
  }
}
