package com.patra.catalog.adapter.scheduler.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.adapter.scheduler.exception.LsiouConfigurationException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// NLM LSIOU 文件名解析工具。
///
/// 从 LSIOU (List of Serials Indexed for Online Users) 数据文件的 URL 中提取版本号。
///
/// **支持的文件名格式**：
/// - LSIOU: `lsi{year}.xml`（如 `lsi2025.xml`）
///
/// **使用示例**：
///
/// ```java
/// String version = LsiouFileNameParser.extractVersion(
///     "ftp://ftp.nlm.nih.gov/online/journals/lsi2025.xml");
/// // 返回 "2025"
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class LsiouFileNameParser {

  /// LSIOU 文件名正则模式：lsi{4位年份}.xml
  private static final Pattern LSIOU_PATTERN = Pattern.compile("lsi(\\d{4})\\.xml");

  private LsiouFileNameParser() {
    // 工具类禁止实例化
  }

  /// 从 URL 提取 LSIOU 版本号。
  ///
  /// @param url LSIOU 数据文件的 URL
  /// @return 版本号（4 位年份字符串，如 "2025"）
  /// @throws LsiouConfigurationException 当 URL 为空、格式无效或文件名不符合规范时
  public static String extractVersion(String url) {
    if (CharSequenceUtil.isBlank(url)) {
      throw new LsiouConfigurationException("URL 不能为空");
    }

    String fileName = extractFileName(url);
    return parseVersionFromFileName(fileName);
  }

  /// 从 URL 中提取文件名。
  ///
  /// @param url 完整 URL
  /// @return 文件名部分
  /// @throws LsiouConfigurationException 当 URL 格式无效时
  private static String extractFileName(String url) {
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
        throw new LsiouConfigurationException("URL 路径为空：" + url);
      }
      int lastSlash = path.lastIndexOf('/');
      return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    } catch (IllegalArgumentException e) {
      throw new LsiouConfigurationException("URL 格式无效：" + url, e);
    }
  }

  /// 从文件名解析版本号。
  ///
  /// @param fileName 文件名（如 lsi2025.xml）
  /// @return 版本号（4 位年份）
  /// @throws LsiouConfigurationException 当文件名不符合规范时
  private static String parseVersionFromFileName(String fileName) {
    Matcher matcher = LSIOU_PATTERN.matcher(fileName);
    if (matcher.find()) {
      return matcher.group(1);
    }

    throw new LsiouConfigurationException("无法从文件名解析 LSIOU 版本号，期望格式为 lsi{year}.xml，实际：" + fileName);
  }
}
