package com.patra.catalog.infra.persistence.converter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.persistence.entity.MeshQualifierDO;
import java.util.ArrayList;
import java.util.List;
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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {};

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
    dataObject.setHistoryNote(aggregate.getHistoryNote());
    dataObject.setOnlineNote(aggregate.getOnlineNote());
    dataObject.setTreeNumbers(convertTreeNumbersToJson(aggregate.getTreeNumbers()));

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
        dataObject.getMeshVersion(),
        dataObject.getHistoryNote(),
        dataObject.getOnlineNote(),
        parseTreeNumbersFromJson(dataObject.getTreeNumbers()));
  }

  /// 将树形编号列表转换为 JSON 字符串。
  ///
  /// @param treeNumbers 树形编号列表
  /// @return JSON 字符串，空列表返回 null
  private String convertTreeNumbersToJson(List<String> treeNumbers) {
    if (CollUtil.isEmpty(treeNumbers)) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(treeNumbers);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize tree numbers to JSON", e);
    }
  }

  /// 从 JSON 字符串解析树形编号列表。
  ///
  /// @param json JSON 字符串
  /// @return 树形编号列表，null 或空字符串返回空列表
  private List<String> parseTreeNumbersFromJson(String json) {
    if (StrUtil.isBlank(json)) {
      return new ArrayList<>();
    }
    try {
      return OBJECT_MAPPER.readValue(json, LIST_STRING_TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse tree numbers from JSON: " + json, e);
    }
  }
}
