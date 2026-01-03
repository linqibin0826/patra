package com.patra.catalog.adapter.scheduler.util;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.adapter.scheduler.exception.MeshConfigurationException;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// MeSH 文件名解析工具。
///
/// 从 MeSH 数据文件的 URL 中提取版本号。
///
/// **支持的文件名格式**：
/// - Descriptor: `desc{year}.xml`（如 `desc2025.xml`）
/// - Qualifier: `qual{year}.xml`（如 `qual2025.xml`）
/// - Supplemental (SCR): `supp{year}.xml`（如 `supp2025.xml`）
///
/// **使用示例**：
///
/// ```java
/// String version = MeshFileNameParser.extractVersion(
///     "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml");
/// // 返回 "2025"
/// ```
///
/// @author linqibin
/// @since 0.1.0
public final class MeshFileNameParser {

  /// Descriptor 文件名正则模式：desc{4位年份}.xml
  private static final Pattern DESCRIPTOR_PATTERN = Pattern.compile("desc(\\d{4})\\.xml");

  /// Qualifier 文件名正则模式：qual{4位年份}.xml
  private static final Pattern QUALIFIER_PATTERN = Pattern.compile("qual(\\d{4})\\.xml");

  /// Supplemental (SCR) 文件名正则模式：supp{4位年份}.xml
  private static final Pattern SUPPLEMENTAL_PATTERN = Pattern.compile("supp(\\d{4})\\.xml");

  private MeshFileNameParser() {
    // 工具类禁止实例化
  }

  /// 从 URL 提取 MeSH 版本号。
  ///
  /// @param url MeSH 数据文件的 URL
  /// @return 版本号（4 位年份字符串，如 "2025"）
  /// @throws MeshConfigurationException 当 URL 为空、格式无效或文件名不符合规范时
  public static String extractVersion(String url) {
    if (CharSequenceUtil.isBlank(url)) {
      throw new MeshConfigurationException("URL 不能为空");
    }

    String fileName = extractFileName(url);
    return parseVersionFromFileName(fileName);
  }

  /// 从 URL 中提取文件名。
  ///
  /// @param url 完整 URL
  /// @return 文件名部分
  /// @throws MeshConfigurationException 当 URL 格式无效时
  private static String extractFileName(String url) {
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
        throw new MeshConfigurationException("URL 路径为空：" + url);
      }
      int lastSlash = path.lastIndexOf('/');
      return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    } catch (IllegalArgumentException e) {
      throw new MeshConfigurationException("URL 格式无效：" + url, e);
    }
  }

  /// 从文件名解析版本号。
  ///
  /// @param fileName 文件名（如 desc2025.xml、qual2024.xml 或 supp2025.xml）
  /// @return 版本号
  /// @throws MeshConfigurationException 当文件名不符合规范时
  private static String parseVersionFromFileName(String fileName) {
    // 尝试匹配 Descriptor 格式
    Matcher descMatcher = DESCRIPTOR_PATTERN.matcher(fileName);
    if (descMatcher.find()) {
      return descMatcher.group(1);
    }

    // 尝试匹配 Qualifier 格式
    Matcher qualMatcher = QUALIFIER_PATTERN.matcher(fileName);
    if (qualMatcher.find()) {
      return qualMatcher.group(1);
    }

    // 尝试匹配 Supplemental (SCR) 格式
    Matcher suppMatcher = SUPPLEMENTAL_PATTERN.matcher(fileName);
    if (suppMatcher.find()) {
      return suppMatcher.group(1);
    }

    throw new MeshConfigurationException(
        "无法从文件名解析 MeSH 版本号，期望格式为 desc{year}.xml、qual{year}.xml 或 supp{year}.xml，实际："
            + fileName);
  }
}
