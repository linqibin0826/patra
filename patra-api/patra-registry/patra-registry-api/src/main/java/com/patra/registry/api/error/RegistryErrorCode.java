package com.patra.registry.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/// Registry 服务错误码目录。
///
/// 错误码遵循 `REG-NNNN` 格式(前缀 + 数字代码),以追加方式添加以保持 API 兼容性。
///
/// 系列划分:
///
/// - 0xxx - 通用 HTTP 错误(委托给 `HttpStdErrors`)
///   - 1xxx - 领域或业务特定错误
///
/// @author linqibin
/// @since 0.1.0
public enum RegistryErrorCode implements ErrorCodeLike {

  // 注意: 0xxx 系列应通过 HttpStdErrors.of("REG") 工厂方法生成

  // ========================================
  // 业务特定代码 (1xxx 系列)
  // ========================================

  // 字典操作 (14xx 系列)

  /// 字典类型未找到(映射到类型级别的 `DictionaryNotFoundException`)。
  REG_1401("REG-1401", 404),

  /// 字典项未找到(映射到项级别的 `DictionaryNotFoundException`)。
  REG_1402("REG-1402", 404),

  /// 字典项已禁用(映射到 `DictionaryItemDisabled`)。
  REG_1403("REG-1403", 422),

  /// 字典类型已存在(映射到 `DictionaryTypeAlreadyExists`)。
  REG_1404("REG-1404", 409),

  /// 字典项已存在(映射到 `DictionaryItemAlreadyExists`)。
  REG_1405("REG-1405", 409),

  /// 字典类型已禁用(映射到 `DictionaryTypeDisabled`)。
  REG_1406("REG-1406", 422),

  /// 字典验证失败(映射到 `DictionaryValidationException`)。
  REG_1407("REG-1407", 422),

  /// 默认字典项缺失(映射到 `DictionaryDefaultItemMissing`)。
  REG_1408("REG-1408", 422),

  /// 字典仓储失败(映射到 `DictionaryRepositoryException`)。
  REG_1409("REG-1409", 500),

  // Registry 通用操作 (15xx 系列)

  /// Registry 配额超限(映射到 `RegistryQuotaExceeded`)。
  REG_1501("REG-1501", 429);

  private final String code;
  private final int httpStatus;

  /// 构造带有 HTTP 状态映射的错误码。
  ///
  /// @param code `REG-NNNN` 格式的错误码
  /// @param httpStatus 关联的 HTTP 状态码
  RegistryErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /// 返回错误码字符串。
  ///
  /// @return `REG-NNNN` 格式的错误码
  @Override
  public String code() {
    return code;
  }

  /// 返回关联的 HTTP 状态码。
  ///
  /// @return HTTP 状态码
  @Override
  public int httpStatus() {
    return httpStatus;
  }

  /// 返回错误码的字符串表示。
  ///
  /// @return 错误码字符串
  @Override
  public String toString() {
    return code;
  }
}
