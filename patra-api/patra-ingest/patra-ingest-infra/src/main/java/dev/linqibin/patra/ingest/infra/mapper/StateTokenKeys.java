package dev.linqibin.patra.ingest.infra.mapper;

/// StateToken 内部键常量
///
/// 定义 {@link dev.linqibin.patra.ingest.domain.model.vo.query.QuerySession#stateToken()} Map 中使用的键名称。
///
/// **用途**：在 {@link QuerySessionTranslator} 构建 stateToken 和 {@link ProviderParameterMapper}
/// 读取 stateToken 之间传递会话状态。
///
/// **注意**：这些是系统内部使用的键，不是外部 API 的参数名。外部 API 参数名定义在 `patra-common-provenance-api` 模块中。
///
/// @author linqibin
/// @since 0.1.0
public final class StateTokenKeys {
  private StateTokenKeys() {
    throw new AssertionError("工具类不应被实例化");
  }

  // ========== PubMed StateToken 键 ==========

  /// PubMed WebEnv（历史服务器会话标识）
  public static final String PUBMED_WEBENV = "webEnv";

  /// PubMed QueryKey（查询键）
  public static final String PUBMED_QUERY_KEY = "queryKey";

  // ========== EPMC StateToken 键 ==========

  /// EPMC 游标标记
  public static final String EPMC_CURSOR_MARK = "cursorMark";

  // ========== DOAJ StateToken 键 ==========

  /// DOAJ 滚动ID（使用 cursorMark 键存储）
  public static final String DOAJ_CURSOR_MARK = "cursorMark";
}
