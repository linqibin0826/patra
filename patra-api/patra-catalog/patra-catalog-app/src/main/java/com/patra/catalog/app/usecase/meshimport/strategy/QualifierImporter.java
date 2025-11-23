package com.patra.catalog.app.usecase.meshimport.strategy;

import static com.patra.catalog.domain.model.enums.MeshDataType.QUALIFIER;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.enums.MeshDataType;
import com.patra.catalog.domain.port.MeshImportRepository;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 限定词导入策略。
///
/// 职责：
///
/// - 导入 Qualifier（限定词）数据
///   - 一次性批量导入（数据量少，约 80 条）
///   - 更新任务聚合根的表进度
///
/// **导入方式**：
///
/// - 一次性收集所有记录（内存占用极小）
///   - 一次性批量保存到数据库
///   - 标记表为已完成
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class QualifierImporter implements MeshDataImporter {

  private final XmlParserPort xmlParserPort;
  private final MeshQualifierRepository meshQualifierRepository;
  private final MeshImportRepository meshImportRepository;
  private final MeshImportConfig meshImportConfig;

  @Override
  public int importData(File xmlFile, MeshImportAggregate aggregate) throws Exception {
    Integer expectedCount = meshImportConfig.getExpectedCountForTable(QUALIFIER.getCode());
    log.info("[Qualifier-Import] 开始导入（一次性批量）| 预期数量: {} 条", expectedCount);

    try (FileInputStream fis = new FileInputStream(xmlFile);
        Stream<MeshQualifierAggregate> stream = xmlParserPort.parseQualifiers(fis)) {

      // 一次性收集所有记录（约 80 条，内存占用极小）
      List<MeshQualifierAggregate> qualifiers = stream.toList();

      // 一次性批量保存
      meshQualifierRepository.saveBatch(qualifiers);

      int totalCount = qualifiers.size();

      // 标记表为已完成（设置实际总数）
      aggregate.markTableAsCompleted(QUALIFIER.getCode(), totalCount);
      meshImportRepository.save(aggregate);

      log.info("[Qualifier-Import] 导入完成 | 实际数量: {} 条", totalCount);
      return totalCount;
    }
  }

  @Override
  public MeshDataType getDataType() {
    return QUALIFIER;
  }
}
