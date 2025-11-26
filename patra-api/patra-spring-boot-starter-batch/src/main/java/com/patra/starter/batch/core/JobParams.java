package com.patra.starter.batch.core;

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
/// @author linqibin
/// @since 0.1.0
public interface JobParams {
}
