package com.patra.catalog.app.usecase.venue.initialize.command;

import com.patra.catalog.app.usecase.venue.initialize.dto.VenueInitializeResult;
import com.patra.common.cqrs.Command;

/// OpenAlex Venue 导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行 Venue 导入。
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持增量或覆盖模式
/// - 如果表中已有数据，导入会直接失败
/// - 如需重新导入，必须先手动清空数据库
///
/// **与 MeshDescriptorImportCommand 的差异**：
///
/// - Venue 不需要 URL 参数（从 OpenAlex S3 Manifest 动态获取分区文件列表）
/// - Venue 不需要版本号（OpenAlex 使用 updated_date 分区管理版本）
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @author linqibin
/// @since 0.1.0
public record VenueInitializeCommand() implements Command<VenueInitializeResult> {

  /// 创建导入命令。
  ///
  /// @return 导入命令
  public static VenueInitializeCommand create() {
    return new VenueInitializeCommand();
  }
}
