package dev.patra.catalog.domain.fulltext.port.source;

import dev.patra.catalog.domain.fulltext.model.vo.PmcArticleMetadata;
import java.net.URI;
import java.util.List;

/// PMC Cloud Service（公开 S3 桶 `pmc-oa-opendata`）的读取端口。
///
/// "不存在"与"出错"必须区分：桶里没有这篇返回空列表；HTTP 出错、超时、响应不合法一律抛
/// `PmcSourceException`，不吞异常返回空。
///
/// @author linqibin
/// @since 0.11.0
public interface PmcArticleSourcePort {

  /// 列出一篇文章在桶里的全部版本前缀。
  ///
  /// @param pmcid PMCID，如 `PMC123`
  /// @return 版本前缀，如 `PMC123.1`、`PMC123.2`；不在桶里返回空列表
  /// @throws dev.patra.catalog.domain.fulltext.exception.PmcSourceException 访问失败时抛出
  List<String> listVersionPrefixes(String pmcid);

  /// 读一个版本的元数据。
  ///
  /// @param versionPrefix 版本前缀
  /// @return 元数据
  /// @throws dev.patra.catalog.domain.fulltext.exception.PmcSourceException 访问失败时抛出
  PmcArticleMetadata getMetadata(String versionPrefix);

  /// 拼出版本目录下某个文件的 HTTPS 地址，给文件下载端口用。
  ///
  /// @param versionPrefix 版本前缀
  /// @param fileName 文件名
  /// @return 下载地址
  URI fileUri(String versionPrefix, String fileName);
}
