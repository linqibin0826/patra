package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.model.enums.MeshFileType;
import com.patra.catalog.domain.port.source.FileDownloadPort;
import com.patra.catalog.domain.port.source.MeshSourceFilePort;
import java.net.URI;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 数据源文件适配器。
///
/// 委托 {@link FileDownloadPort} 从 NLM（美国国家医学图书馆）远程服务器下载 MeSH XML 文件。
///
/// **数据源**：
///
/// - Descriptor（主题词）：`https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc{year}.xml`
/// - Qualifier（限定词）：`https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual{year}.xml`
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshSourceFileAdapter implements MeshSourceFilePort {

  private final FileDownloadPort fileDownloadPort;

  @Override
  public Path fetchFile(MeshFileType type, String meshVersion, URI remoteUrl) {
    log.info("从远程下载 MeSH {} 文件: {}", type.getDescription(), remoteUrl);
    return fileDownloadPort.downloadToTemp(remoteUrl);
  }
}
