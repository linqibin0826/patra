package dev.linqibin.patra.catalog.adapter.scheduler.util;

import cn.hutool.core.text.CharSequenceUtil;
import dev.linqibin.patra.catalog.adapter.scheduler.exception.RorConfigurationException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// ROR 文件名解析工具。
///
/// 从 ROR Data Dump 文件的 URL 中提取版本号。
///
/// **支持的文件名格式**：
///
/// `v{version}-{date}-ror-data.zip`，如 `v2.0-2025-12-16-ror-data.zip`
///
/// **使用示例**：
///
/// ```java
/// String version = RorFileNameParser.extractVersion(
///     "https://zenodo.org/records/17468391/files/v2.0-2025-12-16-ror-data.zip");
/// // 返回 "v2.0"
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class RorFileNameParser {

  /// ROR 文件名正则模式：v{major.minor}-{yyyy-MM-dd}-ror-data.zip
  ///
  /// 示例：`v2.0-2025-12-16-ror-data.zip`
  private static final Pattern ROR_FILE_PATTERN =
      Pattern.compile("(v\\d+\\.\\d+)-\\d{4}-\\d{2}-\\d{2}-ror-data\\.zip");

  private RorFileNameParser() {
    // 工具类禁止实例化
  }

  /// 从 URL 提取 ROR 版本号。
  ///
  /// @param url ROR Data Dump 文件的 URL
  /// @return 版本号（如 "v1.63"）
  /// @throws RorConfigurationException 当 URL 为空、格式无效或文件名不符合规范时
  public static String extractVersion(String url) {
    if (CharSequenceUtil.isBlank(url)) {
      throw new RorConfigurationException("URL 不能为空");
    }

    String fileName = extractFileName(url);
    return parseVersionFromFileName(fileName);
  }

  /// 从 URL 中提取文件名。
  ///
  /// @param url 完整 URL
  /// @return 文件名部分
  /// @throws RorConfigurationException 当 URL 格式无效时
  private static String extractFileName(String url) {
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
        throw new RorConfigurationException("URL 路径为空：" + url);
      }
      int lastSlash = path.lastIndexOf('/');
      return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    } catch (IllegalArgumentException e) {
      throw new RorConfigurationException("URL 格式无效：" + url, e);
    }
  }

  /// 从文件名解析版本号。
  ///
  /// @param fileName 文件名（如 v1.63-2025-01-09-ror-data.json）
  /// @return 版本号（如 "v1.63"）
  /// @throws RorConfigurationException 当文件名不符合规范时
  private static String parseVersionFromFileName(String fileName) {
    Matcher matcher = ROR_FILE_PATTERN.matcher(fileName);
    if (matcher.find()) {
      return matcher.group(1);
    }

    throw new RorConfigurationException(
        "无法从文件名解析 ROR 版本号，期望格式为 v{version}-{date}-ror-data.zip，实际：" + fileName);
  }
}
