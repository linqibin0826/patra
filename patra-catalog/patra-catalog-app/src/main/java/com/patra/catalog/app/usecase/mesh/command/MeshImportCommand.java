package com.patra.catalog.app.usecase.mesh.command;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import java.util.Objects;

/// MeSH 导入命令（Adapter → Application）。
///
/// 由调度任务或外部调用方构建，经 Adapter 层协议转换后传递到应用层执行 MeSH 主题词导入。
///
/// **字段语义与约束**：
///
/// - **filePath**：XML 文件路径，必填，不能为空白
/// - **meshVersion**：MeSH 版本号（如 "2025"），必填，不能为空白
/// - **mode**：导入模式，必填
///
/// **不变量**：
///
/// - 所有字段不为 null 且不为空白（在 compact constructor 中校验）
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @param filePath XML 文件路径（必填）
/// @param meshVersion MeSH 版本号（必填）
/// @param mode 导入模式（必填）
/// @author linqibin
/// @since 0.1.0
public record MeshImportCommand(String filePath, String meshVersion, MeshDescriptorImportMode mode) {

  /// 构造并验证命令参数。
  ///
  /// @throws CatalogScheduleParameterException 当任一必填字段为空时
  public MeshImportCommand {
    if (CharSequenceUtil.isBlank(filePath)) {
      throw new CatalogScheduleParameterException("filePath 参数不能为空");
    }
    if (CharSequenceUtil.isBlank(meshVersion)) {
      throw new CatalogScheduleParameterException("meshVersion 参数不能为空");
    }
    Objects.requireNonNull(mode, "mode 参数不能为空");
  }

  /// 从原始字符串构建命令。
  ///
  /// 将字符串形式的 mode 转换为枚举，支持大小写不敏感。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本号
  /// @param modeStr 导入模式字符串
  /// @return 构建的命令对象
  /// @throws CatalogScheduleParameterException 当参数无效时
  public static MeshImportCommand of(String filePath, String meshVersion, String modeStr) {
    MeshDescriptorImportMode mode = parseMode(modeStr);
    return new MeshImportCommand(filePath, meshVersion, mode);
  }

  /// 解析导入模式枚举。
  private static MeshDescriptorImportMode parseMode(String modeStr) {
    if (CharSequenceUtil.isBlank(modeStr)) {
      throw new CatalogScheduleParameterException("mode 参数不能为空");
    }
    try {
      return MeshDescriptorImportMode.valueOf(modeStr.toUpperCase().trim());
    } catch (IllegalArgumentException ex) {
      throw new CatalogScheduleParameterException(
          "非法的导入模式值：" + modeStr + "，有效值：INCREMENTAL, TRUNCATE_REIMPORT", ex);
    }
  }
}
