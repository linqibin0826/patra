package dev.linqibin.patra.catalog.app.usecase.author.command;

import cn.hutool.core.text.CharSequenceUtil;
import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.patra.catalog.app.usecase.author.dto.AuthorImportResult;
import dev.linqibin.patra.catalog.domain.exception.CatalogScheduleParameterException;
import java.net.URI;

/// PubMed Computed Authors 导入命令（Adapter → Application）。
///
/// 由调度任务构建，经 Adapter 层协议转换后传递到应用层执行 PubMed Computed Authors 导入。
///
/// **字段语义与约束**：
///
/// - **url**：远程 JSON Lines 文件 URL，必填，必须是 HTTP/HTTPS 协议
///
/// **数据源说明**：
///
/// - NLM FTP 站点的 PubMed Computed Authors JSON Lines 文件
/// - 文件约 3.6GB，包含约 2100 万+ 作者记录
/// - JSON Lines 格式（每行一个 JSON 对象）
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持增量或覆盖模式
/// - 如果表中已有数据，导入会直接失败
/// - 如需重新导入，必须先手动清空数据库
///
/// **不变量**：
///
/// - url 不为 null 且不为空白（在 compact constructor 中校验）
/// - url 必须是有效的 HTTP 或 HTTPS URL
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @param url 远程 JSON Lines 文件 URL（必填，HTTP/HTTPS 协议）
/// @author linqibin
/// @since 0.1.0
public record AuthorImportCommand(String url) implements Command<AuthorImportResult> {

  /// 构造并验证命令参数。
  ///
  /// @throws CatalogScheduleParameterException 当 url 为空或格式无效时
  public AuthorImportCommand {
    if (CharSequenceUtil.isBlank(url)) {
      throw new CatalogScheduleParameterException("url 参数不能为空");
    }
    validateUrl(url);
  }

  /// 从原始字符串构建命令。
  ///
  /// @param url 远程 JSON Lines 文件 URL
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static AuthorImportCommand of(String url) {
    return new AuthorImportCommand(url);
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
