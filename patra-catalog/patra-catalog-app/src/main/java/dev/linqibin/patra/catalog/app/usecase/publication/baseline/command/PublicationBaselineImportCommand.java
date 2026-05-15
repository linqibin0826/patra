package dev.linqibin.patra.catalog.app.usecase.publication.baseline.command;

import cn.hutool.core.text.CharSequenceUtil;
import dev.linqibin.commons.cqrs.Command;
import dev.linqibin.patra.catalog.app.usecase.publication.baseline.dto.PublicationBaselineImportResult;
import dev.linqibin.patra.catalog.domain.exception.CatalogScheduleParameterException;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationImportParams;
import java.net.URI;

/// PubMed Baseline 文献导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行文献导入。
///
/// **单文件模式设计**：
///
/// 每次命令只处理一个 XML 文件，通过 `fileIndex` 指定文件索引（1-1334）。
/// 这种设计支持：
///
/// - 测试环境只导入 1 个文件
/// - 生产环境通过 XXL-Job 循环调度批量导入
/// - 断点续传（从指定文件继续）
///
/// **字段语义与约束**：
///
/// - **baseUrl**：FTP 基础 URL，必填，必须是 HTTP/HTTPS 协议
/// - **fileIndex**：文件索引（1-1334），对应 pubmed26n0001.xml.gz ~ pubmed26n1334.xml.gz
///
/// **不变量**：
///
/// - baseUrl 不为 null 且不为空白
/// - baseUrl 必须是有效的 HTTP 或 HTTPS URL
/// - fileIndex 在 1 到 1334 之间
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @param baseUrl FTP 基础 URL（必填，HTTP/HTTPS 协议）
/// @param fileIndex 文件索引（1-1334）
/// @author linqibin
/// @since 0.1.0
public record PublicationBaselineImportCommand(String baseUrl, int fileIndex)
    implements Command<PublicationBaselineImportResult> {

  /// 2025 Baseline 文件总数。
  private static final int TOTAL_FILE_COUNT = PublicationImportParams.TOTAL_FILE_COUNT;

  /// 构造并验证命令参数。
  ///
  /// @throws CatalogScheduleParameterException 当 baseUrl 为空或格式无效时
  /// @throws CatalogScheduleParameterException 当 fileIndex 不在有效范围内时
  public PublicationBaselineImportCommand {
    if (CharSequenceUtil.isBlank(baseUrl)) {
      throw new CatalogScheduleParameterException("baseUrl 参数不能为空");
    }
    validateUrl(baseUrl);
    if (fileIndex < 1 || fileIndex > TOTAL_FILE_COUNT) {
      throw new CatalogScheduleParameterException(
          "fileIndex 必须在 1 到 %d 之间，当前值：%d".formatted(TOTAL_FILE_COUNT, fileIndex));
    }
  }

  /// 从参数构建命令。
  ///
  /// @param baseUrl FTP 基础 URL
  /// @param fileIndex 文件索引
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static PublicationBaselineImportCommand of(String baseUrl, int fileIndex) {
    return new PublicationBaselineImportCommand(baseUrl, fileIndex);
  }

  /// 验证 URL 格式（必须是 HTTP 或 HTTPS 协议）。
  private static void validateUrl(String url) {
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null || !scheme.matches("https?")) {
        throw new CatalogScheduleParameterException("baseUrl 必须是 HTTP 或 HTTPS 协议：" + url);
      }
    } catch (IllegalArgumentException e) {
      throw new CatalogScheduleParameterException("baseUrl 格式无效：" + url, e);
    }
  }
}
