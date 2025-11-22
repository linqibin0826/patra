package com.patra.catalog.app.usecase.meshimport.command;

import java.util.Objects;

/// 开始 MeSH 导入任务命令（Adapter → Application）。
/// 
/// 由 REST 控制器或调度作业构建；经 Adapter 解析/默认值处理后，传递到应用层以：
/// 
/// #### 字段说明
/// 
/// - **taskName**: 任务名称，用于标识导入任务（必填）
///   - **sourceUrl**: MeSH 数据源 URL（必填）
/// 
/// #### 不变量
/// 
/// - `taskName != null && !taskName.isBlank()`
///   - `sourceUrl != null && !sourceUrl.isBlank()`
/// 
/// #### 线程安全
/// 
/// Record 是不可变的，可安全地在多线程间共享。
/// 
/// @param taskName 任务名称（必填）
/// @param sourceUrl 数据源 URL（必填）
/// @author linqibin
/// @since 0.2.0
public record StartImportCommand(String taskName, String sourceUrl) {
  public StartImportCommand {
    Objects.requireNonNull(taskName, "任务名称不能为 null");
    Objects.requireNonNull(sourceUrl, "数据源 URL 不能为 null");

    if (taskName.isBlank()) {
      throw new IllegalArgumentException("任务名称不能为空");
    }
    if (sourceUrl.isBlank()) {
      throw new IllegalArgumentException("数据源 URL 不能为空");
    }
  }
}
