package com.patra.catalog.api.command;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开始 MeSH 导入任务命令。
 *
 * <p>封装开始导入任务的请求参数。
 *
 * <p><b>参数说明</b>：
 *
 * <ul>
 *   <li>{@code sourceUrl} - 数据源 URL（可选，默认从配置读取）
 *   <li>{@code taskName} - 任务名称（可选，默认生成：如 "2025年MeSH数据导入"）
 * </ul>
 *
 * <p><b>参数校验</b>：
 *
 * <ul>
 *   <li>sourceUrl 必须是有效的 HTTP/HTTPS URL
 *   <li>taskName 长度不超过 100 个字符
 * </ul>
 *
 * <p><b>使用示例</b>：
 *
 * <pre>{@code
 * // 使用默认配置
 * StartImportCommand command = new StartImportCommand(null, null);
 *
 * // 指定自定义 URL
 * StartImportCommand command = new StartImportCommand(
 *     "https://nlm.nih.gov/mesh/desc2025.xml",
 *     "2025年MeSH数据首次导入"
 * );
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartImportCommand {

  /**
   * 数据源 URL（可选）。
   *
   * <p>如果为 null，则使用配置文件中的默认值：
   *
   * <pre>
   * patra.catalog.mesh.import.source-url=https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
   * </pre>
   */
  @Pattern(
      regexp = "^https?://.*",
      message = "数据源 URL 必须是有效的 HTTP/HTTPS 地址")
  private String sourceUrl;

  /**
   * 任务名称（可选）。
   *
   * <p>如果为 null，则自动生成任务名称（格式："{year}年MeSH数据导入"）
   */
  @Size(max = 100, message = "任务名称长度不能超过 100 个字符")
  private String taskName;
}
