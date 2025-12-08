package com.patra.catalog.infra.adapter.parser.converter;

import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedLanguage;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedMeshHeading;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedTitleRelation;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialIndexingHistory;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialLanguage;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialMeshHeading;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialRecord;
import com.patra.catalog.infra.adapter.parser.dto.serfile.SerialTitleRelated;
import java.util.List;
import org.springframework.stereotype.Component;

/// Serfile DTO 到领域模型转换器。
///
/// 将 XML 解析产生的 `SerialRecord` DTO 转换为领域层的 `PubmedSerialData` 值对象。
/// 只提取业务层实际使用的字段，实现数据精简。
///
/// **设计说明**：
///
/// - 该转换器位于 Infra 层，负责隔离技术细节和领域模型
/// - DTO 包含 38 个字段，但领域模型只保留 15 个业务所需字段
/// - 子对象转换也进行了精简（如 `SerialMeshHeading` → `PubmedMeshHeading`）
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PubmedSerialConverter {

  /// 将 SerialRecord DTO 转换为 PubmedSerialData 领域模型。
  ///
  /// @param dto XML 解析产生的 DTO
  /// @return 领域层值对象
  public PubmedSerialData toDomainModel(SerialRecord dto) {
    return PubmedSerialData.builder()
        // 标识符
        .nlmUniqueId(dto.nlmUniqueId())
        .issnL(dto.issnL())
        .issnPrint(dto.issnPrint())
        .issnElectronic(dto.issnElectronic())
        // 基本信息
        .title(dto.title())
        .medlineTA(dto.medlineTA())
        .coden(dto.coden())
        .country(dto.country())
        .frequency(dto.frequency())
        // 出版历史
        .publicationFirstYear(dto.publicationFirstYear())
        .publicationEndYear(dto.publicationEndYear())
        // 关联数据
        .languages(convertLanguages(dto.languages()))
        .meshHeadings(convertMeshHeadings(dto.meshHeadings()))
        .titleRelations(convertTitleRelations(dto.titleRelations()))
        .indexingHistories(convertIndexingHistories(dto.indexingHistories()))
        .build();
  }

  /// 转换语言列表。
  private List<PubmedLanguage> convertLanguages(List<SerialLanguage> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream()
        .map(
            dto ->
                dto.isPrimary()
                    ? PubmedLanguage.primary(dto.code())
                    : PubmedLanguage.secondary(dto.code()))
        .toList();
  }

  /// 转换 MeSH 主题词列表。
  private List<PubmedMeshHeading> convertMeshHeadings(List<SerialMeshHeading> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream()
        .map(
            dto ->
                dto.hasQualifier()
                    ? PubmedMeshHeading.withQualifier(
                        dto.descriptorName(), dto.qualifierName(), dto.isMajorTopic())
                    : PubmedMeshHeading.of(dto.descriptorName(), dto.isMajorTopic()))
        .toList();
  }

  /// 转换期刊关联关系列表。
  private List<PubmedTitleRelation> convertTitleRelations(List<SerialTitleRelated> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream()
        .map(dto -> PubmedTitleRelation.of(dto.relatedTitle(), dto.titleType()))
        .toList();
  }

  /// 转换索引历史列表。
  private List<PubmedIndexingHistory> convertIndexingHistories(List<SerialIndexingHistory> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return List.of();
    }
    return dtos.stream()
        .map(
            dto ->
                dto.isCurrentlyIndexed()
                    ? PubmedIndexingHistory.currentIndexing(
                        dto.indexingTreatment(), dto.citationSubset())
                    : PubmedIndexingHistory.historicalIndexing(
                        dto.indexingTreatment(), dto.citationSubset()))
        .toList();
  }
}
