package com.patra.starter.batch.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;

/// Job 参数标记接口。
///
/// 所有 Job 参数类必须实现此接口，以便 `JobLauncherHelper` 进行类型安全的参数传递。
///
/// ## 使用示例
///
/// ```java
/// @Data
/// @Builder
/// public class MeshImportJobParams implements JobParams {
///     private String filePath;
///     private String meshVersion;
/// }
/// ```
///
/// ## 字段类型支持
///
/// Spring Batch JobParameters 原生支持以下类型：
///
/// - `String`
/// - `Long`
/// - `Double`
/// - `Date`
///
/// 其他类型将自动转换为 String（通过 `toString()`）。
///
/// ## 标识参数与非标识参数
///
/// Spring Batch 使用 `JobName + identifying JobParameters` 组合唯一标识一个 `JobInstance`。
/// 断点续传依赖于相同的 `JobInstance`。
///
/// - **标识参数**（默认）：参与 JobInstance 标识，变化会创建新实例
/// - **非标识参数**：不参与标识，可以在重试时变化（如临时文件路径）
///
/// 通过重写 `getNonIdentifyingKeys()` 方法声明哪些字段是非标识参数。
///
/// @author linqibin
/// @since 0.1.0
public interface JobParams {

  /// 获取非标识参数的字段名集合。
  ///
  /// 非标识参数不参与 JobInstance 的唯一标识计算，
  /// 即使这些参数值变化，仍会被视为同一个 JobInstance，
  /// 从而支持断点续传。
  ///
  /// **典型用途**：临时文件路径、运行时配置等不影响业务标识的参数。
  ///
  /// **注意**：此方法使用 `@JsonIgnore` 防止被 Jackson 序列化为 Job 参数。
  ///
  /// @return 非标识参数的字段名集合，默认返回空集合（所有参数都是标识参数）
  @JsonIgnore
  default Set<String> getNonIdentifyingKeys() {
    return Set.of();
  }
}
