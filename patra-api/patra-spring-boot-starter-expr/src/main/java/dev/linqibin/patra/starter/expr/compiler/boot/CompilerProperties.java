package dev.linqibin.patra.starter.expr.compiler.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// 表达式编译器配置属性
///
/// 配置前缀: `patra.expr.compiler`
///
/// ### 核心配置
///
/// - `enabled` - 是否启用表达式编译器(默认: true)
///   - `queryParamBridge.enabled` - 是否启用查询参数桥接,通过 std_key=query 映射(默认: true)
///   - `maxQueryLength` - 查询字符串最大长度(默认: 0 = 禁用)
///   - `warnParamCount` - 参数数量警告阈值(软限制,默认: 0 = 禁用)
///   - `maxParamCount` - 参数数量错误阈值(硬限制,默认: 0 = 禁用)
///
/// ### Registry API 配置
///
/// - `registryApi.enabled` - 是否从 patra-registry 加载规则(默认: true)
///   - `registryApi.operationDefault` - 默认操作类型(默认: "SEARCH")
///
/// ### 使用示例
///
/// ```
///
/// patra:
///   expr:
///     compiler:
///       enabled: true
///       maxQueryLength: 10000
///       warnParamCount: 50
///       maxParamCount: 100
///       queryParamBridge:
///         enabled: true
///       registryApi:
///         enabled: true
///         operationDefault: SEARCH
///
/// ```
///
/// @see CompilerProperties.RegistryApi
/// @see CompilerProperties.QueryParamBridge
/// @since 0.1.0
@ConfigurationProperties(prefix = "patra.expr.compiler")
public class CompilerProperties {

  private boolean enabled = true;
  private final RegistryApi registryApi = new RegistryApi();
  private final QueryParamBridge queryParamBridge = new QueryParamBridge();
  private int maxQueryLength = 0; // 0 = 禁用
  private int warnParamCount = 0; // 0 = 禁用
  private int maxParamCount = 0; // 0 = 禁用

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public RegistryApi getRegistryApi() {
    return registryApi;
  }

  public QueryParamBridge getQueryParamBridge() {
    return queryParamBridge;
  }

  /// 查询字符串最大长度(字符数)。0 表示禁用(无限制)。
  ///
  /// @return 最大查询长度
  public int getMaxQueryLength() {
    return maxQueryLength;
  }

  public void setMaxQueryLength(int maxQueryLength) {
    this.maxQueryLength = maxQueryLength;
  }

  /// 参数数量软限制,达到时触发 W-PARAM-COUNT-LIMIT 警告。0 表示禁用。
  ///
  /// @return 警告阈值
  public int getWarnParamCount() {
    return warnParamCount;
  }

  public void setWarnParamCount(int warnParamCount) {
    this.warnParamCount = warnParamCount;
  }

  /// 参数数量硬限制,超过时触发 E-PARAM-COUNT-LIMIT 错误。0 表示禁用。
  ///
  /// @return 最大参数数量
  public int getMaxParamCount() {
    return maxParamCount;
  }

  public void setMaxParamCount(int maxParamCount) {
    this.maxParamCount = maxParamCount;
  }

  public static class RegistryApi {
    private boolean enabled = true;
    private String operationDefault = "SEARCH";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getOperationDefault() {
      return operationDefault;
    }

    public void setOperationDefault(String operationDefault) {
      this.operationDefault = operationDefault;
    }
  }

  /// 查询参数桥接配置
  ///
  /// 控制是否将聚合的布尔查询通过 std_key=query 映射桥接到数据源参数中。
  ///
  /// ### 工作原理
  ///
  /// - 启用时: 编译器将标准键 std_key=query 映射到具体数据源的查询参数名
  ///   - 禁用时: 不进行查询桥接,查询表达式需要手动处理
  ///
  /// @see docs/expr/03-compiler-bridge-internals.md §3.2, §3.6
  public static class QueryParamBridge {
    private boolean enabled = true;

    /// 启用/禁用查询桥接。 启用时,编译器将 std_key=query 桥接到数据源的参数名。
    ///
    /// @return 是否启用查询桥接
    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }
}
