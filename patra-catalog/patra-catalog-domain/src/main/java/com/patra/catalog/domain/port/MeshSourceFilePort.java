package com.patra.catalog.domain.port;

import com.patra.catalog.domain.exception.FileDownloadException;
import java.net.URI;
import java.nio.file.Path;

/// MeSH 数据源文件端口。
///
/// 负责获取 MeSH 数据源文件（Descriptor/Qualifier XML）到本地临时目录。
/// 实现可决定从对象存储缓存或远程 NLM 服务器获取。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏缓存策略实现细节
/// - Infrastructure 层实现缓存优先策略（对象存储 → 远程下载）
/// - 返回本地文件路径，调用方无需关心数据来源
///
/// **缓存策略**（由实现类决定）：
///
/// 1. 检查对象存储中是否存在缓存文件
/// 2. 存在则从对象存储下载到本地
/// 3. 不存在则从远程 URL 下载，并异步上传到对象存储
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
  /// 优先从对象存储缓存获取，如果缓存不存在则从远程 URL 下载。
  /// 下载后会异步上传到对象存储作为缓存。
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
  /// 优先从对象存储缓存获取，如果缓存不存在则从远程 URL 下载。
  /// 下载后会异步上传到对象存储作为缓存。
  ///
  /// **注意**：调用方负责在使用完毕后清理临时文件。
  ///
  /// @param meshVersion MeSH 版本号（如 "2025"）
  /// @param remoteUrl 远程文件 URL（如 NLM 官方下载地址）
  /// @return 本地临时文件路径
  /// @throws FileDownloadException 获取文件失败时
  Path fetchQualifierFile(String meshVersion, URI remoteUrl);
}
