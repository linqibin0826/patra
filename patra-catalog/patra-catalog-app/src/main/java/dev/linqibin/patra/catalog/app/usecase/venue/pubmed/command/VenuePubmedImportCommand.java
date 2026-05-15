package dev.linqibin.patra.catalog.app.usecase.venue.pubmed.command;

import cn.hutool.core.text.CharSequenceUtil;
import dev.linqibin.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedImportResult;
import dev.linqibin.patra.catalog.domain.exception.CatalogScheduleParameterException;
import dev.linqibin.commons.cqrs.Command;
import java.net.URI;

/// PubMed Venue 数据导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行 PubMed 期刊数据导入。
///
/// **字段语义与约束**：
///
/// - **url**：远程 NLM LSIOU XML 文件 URL，必填，必须是 FTP/HTTP/HTTPS 协议
/// - **lsiouVersion**：LSIOU 版本号（如 "2024"），必填，不能为空白
///
/// **设计说明**：
///
/// 导入操作设计为「增量覆盖」语义：
///
/// - 匹配已有期刊记录时，PubMed 数据覆盖旧数据
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
/// @param url 远程 NLM LSIOU XML 文件 URL（必填，FTP/HTTP/HTTPS 协议）
/// @param lsiouVersion LSIOU 版本号（必填）
/// @author linqibin
/// @since 0.1.0
public record VenuePubmedImportCommand(String url, String lsiouVersion)
    implements Command<VenuePubmedImportResult> {

  /// 构造并验证命令参数。
  ///
  /// @throws CatalogScheduleParameterException 当 url 为空或格式无效时
  /// @throws CatalogScheduleParameterException 当 lsiouVersion 为空时
  public VenuePubmedImportCommand {
    if (CharSequenceUtil.isBlank(url)) {
      throw new CatalogScheduleParameterException("url 参数不能为空");
    }
    validateUrl(url);
    if (CharSequenceUtil.isBlank(lsiouVersion)) {
      throw new CatalogScheduleParameterException("lsiouVersion 参数不能为空");
    }
  }

  /// 从原始字符串构建命令。
  ///
  /// @param url 远程 NLM LSIOU XML 文件 URL
  /// @param lsiouVersion LSIOU 版本号
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static VenuePubmedImportCommand of(String url, String lsiouVersion) {
    return new VenuePubmedImportCommand(url, lsiouVersion);
  }

  /// 验证 URL 格式（必须是 FTP、HTTP 或 HTTPS 协议）。
  private static void validateUrl(String url) {
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null || !scheme.matches("ftp|https?")) {
        throw new CatalogScheduleParameterException("url 必须是 FTP、HTTP 或 HTTPS 协议：" + url);
      }
    } catch (IllegalArgumentException e) {
      throw new CatalogScheduleParameterException("url 格式无效：" + url, e);
    }
  }
}
