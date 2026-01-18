package com.patra.catalog.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/// 目录服务的错误代码目录。
///
/// 错误代码格式: `CAT-NNNN`,其中 CAT 是服务前缀。
///
/// 代码范围:
///
/// - 0xxx: HTTP 标准错误(使用 `HttpStdErrors.of("CAT").*` 工厂方法)
/// - 10xx: MeSH 导入错误
/// - 11xx: 文件下载错误
/// - 12xx: 数据解析错误
/// - 13xx: Venue（期刊）导入错误
/// - 14xx: ROR 机构导入错误
/// - 15xx: Publication 导入错误
///
/// @see com.patra.common.error.codes.ErrorCodeLike
public enum CatalogErrorCode implements ErrorCodeLike {

  // ===== MeSH 导入错误 (10xx) =====

  /// 表示 MeSH 限定词导入失败。
  ///
  /// 在导入 MeSH 限定词数据时发生。可能由于 XML 解析失败、
  /// 数据库写入错误或数据验证问题导致。请检查源文件格式和数据库连接。
  CAT_1001("CAT-1001", 500),

  /// 表示 MeSH 主题词导入失败。
  ///
  /// 在导入 MeSH 主题词数据时发生。由于数据量大（约 35,000 条），
  /// 可能由于批处理作业失败、内存不足或事务超时导致。
  /// 请检查 Spring Batch 作业状态和系统资源。
  CAT_1002("CAT-1002", 500),

  /// 表示 MeSH 数据解析失败。
  ///
  /// 在解析 MeSH XML 文件时发生。可能由于文件格式不正确、
  /// 编码问题或 XML 结构与预期不符导致。请验证源文件完整性。
  CAT_1003("CAT-1003", 422),

  // ===== 文件下载错误 (11xx) =====

  /// 表示远程文件下载失败。
  ///
  /// 在从远程服务器下载数据文件时发生。可能由于网络问题、
  /// 服务器不可达或下载超时导致。请检查网络连接和远程服务器状态。
  ///
  /// **适用场景**：
  ///
  /// - 从 NLM 服务器下载 MeSH XML 文件
  /// - 从 OpenAlex S3 下载 Sources 数据文件
  CAT_1101("CAT-1101", 502),

  // ===== Venue 导入错误 (13xx) =====

  /// 表示 OpenAlex Venue（期刊）导入失败。
  ///
  /// 在导入 OpenAlex Sources 数据时发生。可能由于 JSON 解析失败、
  /// 批处理作业失败、数据库写入错误或系统资源不足导致。
  /// 请检查 Spring Batch 作业状态和系统资源。
  CAT_1301("CAT-1301", 500),

  // ===== ROR 机构导入错误 (14xx) =====

  /// 表示 ROR 机构导入失败。
  ///
  /// 在导入 ROR（Research Organization Registry）机构数据时发生。
  /// 可能由于 JSON 解析失败、批处理作业失败、数据库写入错误或系统资源不足导致。
  /// 请检查 Spring Batch 作业状态和系统资源。
  CAT_1401("CAT-1401", 500),

  // ===== Publication 导入错误 (15xx) =====

  /// 表示 PubMed Baseline 文献导入失败。
  ///
  /// 在导入 PubMed Baseline 文献数据时发生。可能由于：
  ///
  /// - XML 解析失败（gzip 解压或 StAX 解析错误）
  /// - 批处理作业失败（Spring Batch Job 执行异常）
  /// - 数据库写入错误（唯一约束冲突或事务超时）
  /// - 系统资源不足（内存、网络带宽）
  ///
  /// 请检查 Spring Batch 作业状态、网络连接和系统资源。
  CAT_1501("CAT-1501", 500);

  private final String code;
  private final int httpStatus;

  CatalogErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /// 返回错误代码字符串。
  ///
  /// @return 格式为 CAT-NNNN 的错误代码
  @Override
  public String code() {
    return code;
  }

  /// 返回与此错误关联的 HTTP 状态码。
  ///
  /// @return HTTP 状态码 (400-599)
  @Override
  public int httpStatus() {
    return httpStatus;
  }

  /// 返回错误代码的字符串表示形式。
  ///
  /// @return 格式为 CAT-NNNN 的错误代码
  @Override
  public String toString() {
    return code;
  }
}
