package com.patra.common.provenance.api.params;

/// PubMed E-utilities 参数键常量
///
/// 集中定义所有 PubMed API 参数名称，供请求组装器和参数映射使用。
///
/// ### 参数分类
///
/// - **基础参数** - DB, ID
///   - **分页参数** - RETSTART, RETMAX
///   - **格式参数** - RETMODE, RETTYPE
///   - **查询参数** - TERM, SORT, DATETYPE, MINDATE, MAXDATE, FIELD, RELDATE
///   - **历史参数** - USEHISTORY, WEBENV, QUERY_KEY
///   - **身份参数** - API_KEY, TOOL, EMAIL
///
/// @author linqibin
/// @since 0.1.0
public final class PubMedParamKeys {

  /// 私有构造函数,防止实例化工具类。
  ///
  /// @throws AssertionError 总是抛出，因为工具类不应被实例化
  private PubMedParamKeys() {
    throw new AssertionError("工具类不应被实例化");
  }

  // ========== 基础参数 ==========

  /// 数据库名称
  public static final String DB = "db";

  /// 文档ID列表（逗号分隔）
  public static final String ID = "id";

  // ========== 分页参数 ==========

  /// 起始位置（从 0 开始）
  public static final String RETSTART = "retstart";

  /// 最大返回数量
  public static final String RETMAX = "retmax";

  // ========== 格式参数 ==========

  /// 返回格式（json/xml/text）
  public static final String RETMODE = "retmode";

  /// 返回类型（uilist/count/abstract）
  public static final String RETTYPE = "rettype";

  // ========== 查询参数 ==========

  /// 搜索词
  public static final String TERM = "term";

  /// 排序方式
  public static final String SORT = "sort";

  /// 日期类型（pdat/edat/mdat）
  public static final String DATETYPE = "datetype";

  /// 最小日期
  public static final String MINDATE = "mindate";

  /// 最大日期
  public static final String MAXDATE = "maxdate";

  /// 搜索字段
  public static final String FIELD = "field";

  /// 相对日期（天数）
  public static final String RELDATE = "reldate";

  // ========== 历史参数 ==========

  /// 是否使用历史服务器（y/n）
  public static final String USEHISTORY = "usehistory";

  /// WebEnv（历史服务器会话标识，注意：PubMed 使用驼峰命名）
  public static final String WEBENV = "WebEnv";

  /// QueryKey（查询键）
  public static final String QUERY_KEY = "query_key";

  // ========== 身份参数 ==========

  /// API 密钥
  public static final String API_KEY = "api_key";

  /// 工具名称
  public static final String TOOL = "tool";

  /// 联系邮箱
  public static final String EMAIL = "email";
}
