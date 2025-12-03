package com.patra.catalog.app.usecase.venue.command;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import com.patra.catalog.domain.model.enums.DataImportMode;
import java.util.Objects;

/// OpenAlex Venue 导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行 Venue 导入。
///
/// **字段语义与约束**：
///
/// - **mode**：导入模式，必填
///   - `INCREMENTAL`：增量导入（Upsert），支持断点续传
///   - `TRUNCATE_REIMPORT`：清空重导入，先删除所有数据再重新导入
///
/// **与 MeshDescriptorImportCommand 的差异**：
///
/// - Venue 不需要 URL 参数（从 OpenAlex S3 Manifest 动态获取分区文件列表）
/// - Venue 不需要版本号（OpenAlex 使用 updated_date 分区管理版本）
/// - Venue 使用 Upsert 策略（增量模式下更新已存在记录）
///
/// **不变量**：
///
/// - mode 不为 null
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @param mode 导入模式（必填）
/// @author linqibin
/// @since 0.1.0
public record VenueImportCommand(DataImportMode mode) {

  /// 构造并验证命令参数。
  ///
  /// @throws NullPointerException 当 mode 为 null 时
  public VenueImportCommand {
    Objects.requireNonNull(mode, "mode 参数不能为空");
  }

  /// 从原始字符串构建命令。
  ///
  /// 将字符串形式的 mode 转换为枚举，支持大小写不敏感。
  ///
  /// @param modeStr 导入模式字符串
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static VenueImportCommand of(String modeStr) {
    DataImportMode mode = parseMode(modeStr);
    return new VenueImportCommand(mode);
  }

  /// 创建增量导入命令（默认模式）。
  ///
  /// @return 增量导入命令
  public static VenueImportCommand incremental() {
    return new VenueImportCommand(DataImportMode.INCREMENTAL);
  }

  /// 创建清空重导入命令。
  ///
  /// @return 清空重导入命令
  public static VenueImportCommand truncateReimport() {
    return new VenueImportCommand(DataImportMode.TRUNCATE_REIMPORT);
  }

  /// 解析导入模式枚举。
  private static DataImportMode parseMode(String modeStr) {
    if (CharSequenceUtil.isBlank(modeStr)) {
      throw new CatalogScheduleParameterException("mode 参数不能为空");
    }
    try {
      return DataImportMode.valueOf(modeStr.toUpperCase().trim());
    } catch (IllegalArgumentException ex) {
      throw new CatalogScheduleParameterException(
          "非法的导入模式值：" + modeStr + "，有效值：INCREMENTAL, TRUNCATE_REIMPORT", ex);
    }
  }
}
