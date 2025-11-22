package com.patra.catalog.app.usecase.meshimport.validator;

import com.patra.catalog.app.config.MeshImportConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 数据量验证器。
/// 
/// 职责：
/// 
/// - 验证导入的数据量是否符合预期
///   - 计算实际数量与预期数量的差异百分比
///   - 差异超过阈值（默认 5%）生成警告信息
/// 
/// **验证规则**：
/// 
/// - 预期数量从 {@link MeshImportConfig} 读取
///   - 差异百分比 = |实际数量 - 预期数量| / 预期数量 × 100%
///   - 差异超过阈值时生成警告（不阻塞任务完成）
/// 
/// **预期数量配置**：
/// 
/// ```
/// 
/// patra.catalog.mesh.import.expected-counts.descriptor=35000
/// patra.catalog.mesh.import.expected-counts.qualifier=80
/// patra.catalog.mesh.import.expected-counts.tree-number=80000
/// patra.catalog.mesh.import.expected-counts.entry-term=250000
/// patra.catalog.mesh.import.expected-counts.concept=180000
/// patra.catalog.mesh.import.count-difference-threshold=5.0
/// 
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshDataValidator {

  private final MeshImportConfig meshImportConfig;

  /// 验证数据量。
/// 
/// 对比实际导入数量与预期数量，生成验证结果。
/// 
/// 验证逻辑：
/// 
/// @param actualCounts 实际导入数量映射（key=表名, value=实际数量）
/// @return 验证结果（包含 isValid 和 warnings 列表）
  public ValidationResult validateDataCounts(Map<String, Integer> actualCounts) {
    log.info("开始验证数据量，实际导入表数：{}", actualCounts.size());

    List<String> warnings = new ArrayList<>();
    boolean isValid = true;

    // 获取配置的容忍阈值（默认 5%）
    double threshold = meshImportConfig.getCountDifferenceThreshold();

    // 验证每张表的数据量
    for (Map.Entry<String, Integer> entry : actualCounts.entrySet()) {
      String tableName = entry.getKey();
      Integer actualCount = entry.getValue();
      Integer expectedCount = meshImportConfig.getExpectedCountForTable(tableName);

      // 如果配置中没有预期值，跳过验证
      if (expectedCount == null || expectedCount == 0) {
        log.debug("表 [{}] 没有配置预期数量，跳过验证", tableName);
        continue;
      }

      // 计算差异百分比
      double differencePercentage = calculateDifferencePercentage(actualCount, expectedCount);

      // 记录验证结果
      log.info(
          "表 [{}] 数据量验证: 预期 {}, 实际 {}, 差异 {:.2f}%",
          tableName,
          expectedCount,
          actualCount,
          differencePercentage);

      // 差异超过阈值时生成警告
      if (differencePercentage > threshold) {
        String warning =
            String.format(
                "表 [%s] 数据量差异超过 %.1f%%: 预期 %d, 实际 %d (差异 %.2f%%)",
                tableName, threshold, expectedCount, actualCount, differencePercentage);
        warnings.add(warning);
        isValid = false;
        log.warn(warning);
      }
    }

    // 汇总验证结果
    if (isValid) {
      log.info("数据量验证通过，所有表数据量在容忍范围内");
    } else {
      log.warn("数据量验证发现 {} 个警告", warnings.size());
    }

    return new ValidationResult(isValid, warnings);
  }

  /// 计算差异百分比。
/// 
/// 公式：|实际数量 - 预期数量| / 预期数量 × 100%
/// 
/// @param actualCount 实际数量
/// @param expectedCount 预期数量
/// @return 差异百分比（0-100）
  private double calculateDifferencePercentage(Integer actualCount, Integer expectedCount) {
    if (expectedCount == 0) {
      return 0.0;
    }
    int difference = Math.abs(actualCount - expectedCount);
    return (difference * 100.0) / expectedCount;
  }

  /// 验证结果记录。
/// 
/// 封装验证结果，包含是否有效和警告列表。
/// 
/// @param isValid 是否有效（所有表差异都在阈值内）
/// @param warnings 警告信息列表（差异超过阈值的表）
  public record ValidationResult(boolean isValid, List<String> warnings) {

    /// 构造验证结果。
/// 
/// @param isValid 是否有效
/// @param warnings 警告列表（不可变）
    public ValidationResult {
      warnings = List.copyOf(warnings); // 确保不可变
    }

    /// 判断是否有警告。
/// 
/// @return true 如果有警告
    public boolean hasWarnings() {
      return !warnings.isEmpty();
    }

    /// 获取警告数量。
/// 
/// @return 警告数量
    public int warningCount() {
      return warnings.size();
    }
  }
}
