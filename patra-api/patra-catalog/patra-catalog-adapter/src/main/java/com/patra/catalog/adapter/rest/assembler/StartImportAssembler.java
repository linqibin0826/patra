package com.patra.catalog.adapter.rest.assembler;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.catalog.adapter.rest.request.StartImportRequest;
import com.patra.catalog.app.config.MeshImportConfig;
import com.patra.catalog.app.usecase.meshimport.command.StartImportCommand;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// StartImportCommand 装配器（Request → Command）。
/// 
/// 职责：将外部 HTTP 请求（{@link StartImportRequest}）转换为内部应用层命令（{@link StartImportCommand}）。
/// 
/// 装配逻辑：
/// 
/// - 解析并验证请求参数
///   - 应用默认值（从配置文件读取）
///   - 生成缺失的数据（如任务名称）
/// 
/// **设计模式**：DTO Assembler（Martin Fowler - Patterns of Enterprise Application
/// Architecture）
/// 
/// **架构层次**：Adapter 层 → Application 层
/// 
/// **职责边界**：
/// 
/// - ✅ 数据格式转换（Request → Command）
///   - ✅ 应用默认值和配置
///   - ✅ 数据规范化（trim、null 处理）
///   - ❌ 业务逻辑校验（由 Domain 层负责）
///   - ❌ 参数格式校验（由 Request 的 @Valid 注解负责）
/// 
/// @author linqibin
/// @since 0.2.0
@Component
@RequiredArgsConstructor
public class StartImportAssembler {

  private final MeshImportConfig meshImportConfig;

  /// 将 HTTP 请求装配为应用层命令。
/// 
/// @param request HTTP 请求参数（可为 null，表示使用完全默认配置）
/// @return 应用层命令
  public StartImportCommand assemble(StartImportRequest request) {
    // 如果请求为空，使用完全默认配置
    if (request == null) {
      return new StartImportCommand(generateDefaultTaskName(), meshImportConfig.getSourceUrl());
    }

    // 解析 sourceUrl（优先使用请求参数，否则使用配置）
    String sourceUrl = resolveSourceUrl(request.getSourceUrl());

    // 解析 taskName（优先使用请求参数，否则生成默认名称）
    String taskName = resolveTaskName(request.getTaskName());

    return new StartImportCommand(taskName, sourceUrl);
  }

  /// 解析数据源 URL。
/// 
/// @param requestSourceUrl 请求中的 sourceUrl
/// @return 解析后的 sourceUrl
  private String resolveSourceUrl(String requestSourceUrl) {
    return CharSequenceUtil.isNotBlank(requestSourceUrl)
        ? CharSequenceUtil.trim(requestSourceUrl)
        : meshImportConfig.getSourceUrl();
  }

  /// 解析任务名称。
/// 
/// @param requestTaskName 请求中的 taskName
/// @return 解析后的 taskName
  private String resolveTaskName(String requestTaskName) {
    return CharSequenceUtil.isNotBlank(requestTaskName)
        ? CharSequenceUtil.trim(requestTaskName)
        : generateDefaultTaskName();
  }

  /// 生成默认任务名称。
/// 
/// 格式："{year}年MeSH数据导入"
/// 
/// @return 默认任务名称
  private String generateDefaultTaskName() {
    int currentYear = Year.now().getValue();
    return currentYear + "年MeSH数据导入";
  }
}
