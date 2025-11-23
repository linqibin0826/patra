package com.patra.catalog.app.usecase.meshimport.strategy;

import static com.patra.catalog.domain.model.enums.MeshDataType.CONCEPT;

import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.enums.MeshDataType;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshImportRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.FileInputStream;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 概念导入策略。
///
/// 职责：
///
/// - 导入 Concept（概念）数据
///   - 批次流式导入（数据量大，约 180000 条）
///   - 每批次独立事务（REQUIRES_NEW）
///   - 更新任务聚合根的表进度
///
/// **导入方式**：
///
/// - 流式消费 XML 解析结果
///   - 分批保存到数据库（避免内存溢出）
///   - 每批次更新进度（支持断点续传）
///
/// **事务管理**：
///
/// - 继承 `AbstractBatchImporter`，自动获得批次级别的独立事务
///   - 每批次 `REQUIRES_NEW` 传播级别
///   - 失败不影响已完成批次
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class ConceptImporter extends AbstractBatchImporter<MeshConcept> {

  private final XmlParserPort xmlParserPort;
  private final MeshDescriptorRepository meshDescriptorRepository;

  public ConceptImporter(
      XmlParserPort xmlParserPort,
      MeshDescriptorRepository meshDescriptorRepository,
      MeshImportRepository meshImportRepository,
      MeshImportConfig meshImportConfig) {
    super(meshImportRepository, meshImportConfig);
    this.xmlParserPort = xmlParserPort;
    this.meshDescriptorRepository = meshDescriptorRepository;
  }

  @Override
  protected Stream<MeshConcept> parseStream(FileInputStream fis) throws Exception {
    return xmlParserPort.parseConcepts(fis);
  }

  @Override
  protected void saveBatch(List<MeshConcept> batch) {
    meshDescriptorRepository.saveConceptsBatch(batch);
  }

  @Override
  protected String getLogTag() {
    return "[Concept-Import]";
  }

  @Override
  public MeshDataType getDataType() {
    return CONCEPT;
  }
}
