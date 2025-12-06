package com.patra.catalog.app.usecase.serfile.command;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import java.net.URI;

/// NLM Serfile 导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行 Serfile 期刊数据导入。
///
/// **字段语义与约束**：
///
/// - **url**：远程 XML 文件 URL，必填，必须是 HTTP/HTTPS 协议
/// - **serfileVersion**：Serfile 版本号（如 "2025"），必填，不能为空白
///
/// **设计说明**：
///
/// 导入操作设计为「增量覆盖」语义：
///
/// - 匹配已有期刊记录时，PubMed 数据完全覆盖 OpenAlex 数据
/// - 匹配策略：ISSN-L → NLM ID → ISSN（降级策略）
/// - PubMed 独有期刊将创建新的 VenueAggregate 记录
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
/// @param serfileVersion Serfile 版本号（必填）
/// @author linqibin
/// @since 0.1.0
public record SerfileImportCommand(String url, String serfileVersion) {

  /// 构造并验证命令参数。
  ///
  /// @throws CatalogScheduleParameterException 当 url 为空或格式无效时
  /// @throws CatalogScheduleParameterException 当 serfileVersion 为空时
  public SerfileImportCommand {
    if (CharSequenceUtil.isBlank(url)) {
      throw new CatalogScheduleParameterException("url 参数不能为空");
    }
    validateUrl(url);
    if (CharSequenceUtil.isBlank(serfileVersion)) {
      throw new CatalogScheduleParameterException("serfileVersion 参数不能为空");
    }
  }

  /// 从原始字符串构建命令。
  ///
  /// @param url 远程 XML 文件 URL
  /// @param serfileVersion Serfile 版本号
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static SerfileImportCommand of(String url, String serfileVersion) {
    return new SerfileImportCommand(url, serfileVersion);
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
}
