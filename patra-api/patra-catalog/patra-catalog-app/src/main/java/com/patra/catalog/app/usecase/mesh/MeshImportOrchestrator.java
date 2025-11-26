package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.MeshDescriptorBatchPort;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.domain.port.XmlParserPort;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 数据导入编排器。
///
/// **职责**：
///
/// - 编排 MeSH 数据导入流程
/// - 管理事务边界
/// - 委派具体任务给领域端口
///
/// **导入顺序**：
///
/// 1. 先导入 Qualifier（限定词，约 100 条，同步事务）
/// 2. 再导入 Descriptor（主题词，约 35,000 条，批处理）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class MeshImportOrchestrator implements MeshImportUseCase {

  private final XmlParserPort xmlParserPort;
  private final MeshQualifierRepository qualifierRepository;
  private final MeshDescriptorRepository descriptorRepository;
  private final MeshDescriptorBatchPort meshDescriptorBatchPort;

  /// 导入 MeSH 限定词。
  ///
  /// 小数据量（约 100 条），不使用 Spring Batch，直接在事务内批量保存。
  ///
  /// @param xmlInputStream 限定词 XML 文件输入流
  /// @return 导入的限定词数量
  @Transactional
  public int importQualifiers(InputStream xmlInputStream) {
    log.info("开始导入 MeSH 限定词");

    // 使用 XmlParserPort 解析 XML
    List<MeshQualifierAggregate> qualifiers =
        xmlParserPort.parseQualifiers(xmlInputStream).toList();

    // 批量保存
    qualifierRepository.saveBatch(qualifiers);

    log.info("MeSH 限定词导入完成，数量：{}", qualifiers.size());
    return qualifiers.size();
  }

  /// 导入 MeSH 主题词。
  ///
  /// 大数据量（约 35,000 条），使用批处理进行导入。
  ///
  /// **导入模式**：
  ///
  /// - `INCREMENTAL`：增量导入，幂等执行，支持断点续传
  /// - `TRUNCATE_REIMPORT`：清空重导入，先 TRUNCATE 所有表再重新导入
  ///
  /// **注意**（TRUNCATE_REIMPORT 模式）：
  ///
  /// - TRUNCATE 是 DDL 操作，会隐式提交事务，无法回滚
  /// - 清空操作会删除所有版本的数据，不仅仅是指定版本
  /// - 如果清空成功但导入失败，需要重新执行此方法
  ///
  /// @param command 导入命令（包含文件路径、版本、模式）
  /// @return 导入结果
  @Override
  public MeshImportResult importDescriptors(MeshImportCommand command) {
    log.info(
        "启动 MeSH 主题词导入，文件：{}，版本：{}，模式：{}",
        command.filePath(),
        command.meshVersion(),
        command.mode());

    if (command.mode() == MeshDescriptorImportMode.TRUNCATE_REIMPORT) {
      descriptorRepository.truncateAll();
      log.info("已清空所有旧数据");
    }

    boolean forceNewInstance = (command.mode() == MeshDescriptorImportMode.TRUNCATE_REIMPORT);
    MeshImportParams params =
        new MeshImportParams(command.filePath(), command.meshVersion(), forceNewInstance);
    Long executionId = meshDescriptorBatchPort.launchImport(params);

    return MeshImportResult.success(
        executionId, command.filePath(), command.meshVersion(), command.mode());
  }
}
