package com.patra.catalog.domain.port;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;
import java.nio.file.Path;

/// MeSH 数据源文件端口。
///
/// 负责从 NLM（美国国家医学图书馆）远程服务器获取 MeSH 数据源文件到本地临时目录。
///
/// **数据源**：
///
/// - Descriptor（主题词）：`https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc{year}.xml`
/// - Qualifier（限定词）：`https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual{year}.xml`
///
/// **设计原则**：
///
/// - Domain 层定义接口，Infrastructure 层提供实现
/// - 返回本地临时文件路径，调用方负责使用完毕后清理
///
/// **使用场景**：
///
/// - MeSH Descriptor 数据导入（约 300MB XML 文件）
/// - MeSH Qualifier 数据导入
///
/// @author linqibin
/// @since 0.1.0
public interface MeshSourceFilePort {

  /// 获取 MeSH Descriptor 源文件到本地临时目录。
  ///
  /// 从指定的远程 URL 下载文件到本地临时目录。
  ///
  /// **注意**：调用方负责在使用完毕后清理临时文件。
  ///
  /// @param meshVersion MeSH 版本号（如 "2025"）
  /// @param remoteUrl 远程文件 URL（如 NLM 官方下载地址）
  /// @return 本地临时文件路径
  /// @throws FileDownloadException 获取文件失败时
  Path fetchDescriptorFile(String meshVersion, URI remoteUrl);

  /// 获取 MeSH Qualifier 源文件到本地临时目录。
  ///
  /// 从指定的远程 URL 下载文件到本地临时目录。
  ///
  /// **注意**：调用方负责在使用完毕后清理临时文件。
  ///
  /// @param meshVersion MeSH 版本号（如 "2025"）
  /// @param remoteUrl 远程文件 URL（如 NLM 官方下载地址）
  /// @return 本地临时文件路径
  /// @throws FileDownloadException 获取文件失败时
  Path fetchQualifierFile(String meshVersion, URI remoteUrl);
}
