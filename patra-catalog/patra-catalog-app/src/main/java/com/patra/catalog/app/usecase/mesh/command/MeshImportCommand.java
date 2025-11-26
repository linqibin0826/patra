package com.patra.catalog.app.usecase.mesh.command;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import java.net.URI;
import java.util.Objects;

/// MeSH 导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行 MeSH 主题词导入。
///
/// **字段语义与约束**：
///
/// - **url**：远程 XML 文件 URL，必填，必须是 HTTP/HTTPS 协议
/// - **meshVersion**：MeSH 版本号（如 "2025"），必填，不能为空白
/// - **mode**：导入模式，必填
///
/// **不变量**：
///
/// - 所有字段不为 null 且不为空白（在 compact constructor 中校验）
/// - url 必须是有效的 HTTP 或 HTTPS URL
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @param url 远程 XML 文件 URL（必填，HTTP/HTTPS 协议）
/// @param meshVersion MeSH 版本号（必填）
/// @param mode 导入模式（必填）
/// @author linqibin
/// @since 0.1.0
public record MeshImportCommand(String url, String meshVersion, MeshDescriptorImportMode mode) {

  /// 构造并验证命令参数。
  ///
  /// @throws CatalogScheduleParameterException 当 url 为空或格式无效时
  /// @throws CatalogScheduleParameterException 当 meshVersion 为空时
  /// @throws NullPointerException 当 mode 为 null 时
  public MeshImportCommand {
    if (CharSequenceUtil.isBlank(url)) {
      throw new CatalogScheduleParameterException("url 参数不能为空");
    }
    validateUrl(url);
    if (CharSequenceUtil.isBlank(meshVersion)) {
      throw new CatalogScheduleParameterException("meshVersion 参数不能为空");
    }
    Objects.requireNonNull(mode, "mode 参数不能为空");
  }

  /// 从原始字符串构建命令。
  ///
  /// 将字符串形式的 mode 转换为枚举，支持大小写不敏感。
  ///
  /// @param url 远程 XML 文件 URL
  /// @param meshVersion MeSH 版本号
  /// @param modeStr 导入模式字符串
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static MeshImportCommand of(String url, String meshVersion, String modeStr) {
    MeshDescriptorImportMode mode = parseMode(modeStr);
    return new MeshImportCommand(url, meshVersion, mode);
  }

  /// 验证 URL 格式（必须是 HTTP 或 HTTPS 协议）。
  private static void validateUrl(String url) {
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null || !scheme.matches("https?")) {
        throw new CatalogScheduleParameterException("url 必须是 HTTP 或 HTTPS 协议：" + url);
      }
    } catch (IllegalArgumentException e) {
      throw new CatalogScheduleParameterException("url 格式无效：" + url, e);
    }
  }

  /// 解析导入模式枚举。
  private static MeshDescriptorImportMode parseMode(String modeStr) {
    if (CharSequenceUtil.isBlank(modeStr)) {
      throw new CatalogScheduleParameterException("mode 参数不能为空");
    }
    try {
      return MeshDescriptorImportMode.valueOf(modeStr.toUpperCase().trim());
    } catch (IllegalArgumentException ex) {
      throw new CatalogScheduleParameterException(
          "非法的导入模式值：" + modeStr + "，有效值：INCREMENTAL, TRUNCATE_REIMPORT", ex);
    }
  }
}
