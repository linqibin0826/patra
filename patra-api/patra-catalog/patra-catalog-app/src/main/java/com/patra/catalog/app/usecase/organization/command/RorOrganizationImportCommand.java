package com.patra.catalog.app.usecase.organization.command;

import dev.linqibin.commons.cqrs.Command;
import java.util.Objects;

/// ROR 机构导入命令。
///
/// 触发 ROR 机构批量导入任务。
///
/// **参数说明**：
///
/// - `url`：ROR Data Dump JSON 文件下载 URL
/// - `rorVersion`：ROR 版本号（如 "v1.63"）
///
/// @author linqibin
/// @since 0.1.0
public record RorOrganizationImportCommand(String url, String rorVersion)
    implements Command<RorOrganizationImportResult> {

  /// 验证命令参数。
  public RorOrganizationImportCommand {
    Objects.requireNonNull(url, "url 不能为空");
    Objects.requireNonNull(rorVersion, "rorVersion 不能为空");
  }
}
