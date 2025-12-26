package com.patra.catalog.adapter.scheduler.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.adapter.scheduler.exception.SerfileConfigurationException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// NLM Serfile 文件名解析工具。
///
/// 从 Serfile 数据文件的 URL 中提取版本号。
///
/// **支持的文件名格式**：
/// - SerfileBase: `serfilebase.{year}.xml`（如 `serfilebase.2025.xml`）
/// - Serfile 更新: `serfile{yymmdd}.xml`（如 `serfile250101.xml`，表示 2025-01-01）
///
/// **使用示例**：
///
/// ```java
/// String version = SerfileFileNameParser.extractVersion(
///     "https://ftp.nlm.nih.gov/projects/serfilelease/serfilebase.2025.xml");
/// // 返回 "2025"
///
/// String updateVersion = SerfileFileNameParser.extractVersion(
///     "https://ftp.nlm.nih.gov/projects/serfilelease/serfile250101.xml");
/// // 返回 "250101"
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class SerfileFileNameParser {

  /// SerfileBase 文件名正则模式：serfilebase.{4位年份}.xml
  private static final Pattern SERFILEBASE_PATTERN = Pattern.compile("serfilebase\\.(\\d{4})\\.xml");

  /// Serfile 更新文件名正则模式：serfile{6位日期 yymmdd}.xml
  private static final Pattern SERFILE_UPDATE_PATTERN = Pattern.compile("serfile(\\d{6})\\.xml");

  private SerfileFileNameParser() {
    // 工具类禁止实例化
  }

  /// 从 URL 提取 Serfile 版本号。
  ///
  /// @param url Serfile 数据文件的 URL
  /// @return 版本号（4 位年份或 6 位日期字符串，如 "2025" 或 "250101"）
  /// @throws SerfileConfigurationException 当 URL 为空、格式无效或文件名不符合规范时
  public static String extractVersion(String url) {
    if (CharSequenceUtil.isBlank(url)) {
      throw new SerfileConfigurationException("URL 不能为空");
    }

    String fileName = extractFileName(url);
    return parseVersionFromFileName(fileName);
  }

  /// 从 URL 中提取文件名。
  ///
  /// @param url 完整 URL
  /// @return 文件名部分
  /// @throws SerfileConfigurationException 当 URL 格式无效时
  private static String extractFileName(String url) {
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
        throw new SerfileConfigurationException("URL 路径为空：" + url);
      }
      int lastSlash = path.lastIndexOf('/');
      return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    } catch (IllegalArgumentException e) {
      throw new SerfileConfigurationException("URL 格式无效：" + url, e);
    }
  }

  /// 从文件名解析版本号。
  ///
  /// @param fileName 文件名（如 serfilebase.2025.xml 或 serfile250101.xml）
  /// @return 版本号
  /// @throws SerfileConfigurationException 当文件名不符合规范时
  private static String parseVersionFromFileName(String fileName) {
    // 尝试匹配 SerfileBase 格式（年度基础文件）
    Matcher baseMatcher = SERFILEBASE_PATTERN.matcher(fileName);
    if (baseMatcher.find()) {
      return baseMatcher.group(1);
    }

    // 尝试匹配 Serfile 更新格式（增量更新文件）
    Matcher updateMatcher = SERFILE_UPDATE_PATTERN.matcher(fileName);
    if (updateMatcher.find()) {
      return updateMatcher.group(1);
    }

    throw new SerfileConfigurationException(
        "无法从文件名解析 Serfile 版本号，期望格式为 serfilebase.{year}.xml 或 serfile{yymmdd}.xml，实际：" + fileName);
  }
}
