package com.patra.catalog.domain.model.vo.publication;

/// Publication Baseline 批量导入参数值对象。
///
/// 封装 PubMed Baseline 文献批量导入所需的参数，用于
/// `PublicationBatchPort.launchBaselineImport()` 方法调用。
///
/// **单文件模式设计**：
///
/// 每次 Job 执行只处理一个文件，通过 `fileIndex` 指定文件索引（1-1334）。
/// 这种设计支持：
///
/// - 测试环境只导入 1 个文件
/// - 生产环境通过循环调度批量导入
/// - 断点续传（从指定文件继续）
///
/// **参数说明**：
///
/// - `baseUrl`：FTP 基础 URL（如 `https://ftp.ncbi.nlm.nih.gov/pubmed/baseline/`）
/// - `fileIndex`：文件索引（1-1334，对应 pubmed26n0001.xml.gz ~ pubmed26n1334.xml.gz）
///
/// **URL 生成规则**：
///
/// `{baseUrl}pubmed26n{fileIndex:04d}.xml.gz`
///
/// 示例：fileIndex=1 → `pubmed26n0001.xml.gz`
///
/// @author linqibin
/// @since 0.1.0
public record PublicationImportParams(String baseUrl, int fileIndex) {

  /// 2026 Baseline 文件总数。
  public static final int TOTAL_FILE_COUNT = 1334;

  /// 文件名模板。
  private static final String FILE_NAME_TEMPLATE = "pubmed26n%04d.xml.gz";

  /// 创建导入参数。
  ///
  /// @param baseUrl FTP 基础 URL
  /// @param fileIndex 文件索引（1-1334）
  public PublicationImportParams {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl 不能为空");
    }
    if (fileIndex < 1 || fileIndex > TOTAL_FILE_COUNT) {
      throw new IllegalArgumentException(
          "fileIndex 必须在 1 到 %d 之间，当前值：%d".formatted(TOTAL_FILE_COUNT, fileIndex));
    }
  }

  /// 创建导入参数。
  ///
  /// @param baseUrl FTP 基础 URL
  /// @param fileIndex 文件索引
  /// @return 导入参数
  public static PublicationImportParams of(String baseUrl, int fileIndex) {
    return new PublicationImportParams(baseUrl, fileIndex);
  }

  /// 获取文件名。
  ///
  /// @return 文件名（如 pubmed26n0001.xml.gz）
  public String getFileName() {
    return FILE_NAME_TEMPLATE.formatted(fileIndex);
  }

  /// 获取完整的下载 URL。
  ///
  /// @return 完整下载 URL
  public String getDownloadUrl() {
    String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    return normalizedBaseUrl + getFileName();
  }
}
