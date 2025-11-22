package com.patra.ingest.app.usecase.plan.slicer;

import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.util.List;

/// 切片规划策略接口(应用层·策略接口)
///
/// 定义不同切片策略的通用能力,包括策略标识符和基于上下文将时间窗口分解为多个切片的逻辑。
///
/// 实现类必须保证:
///
/// - code 唯一,以便从注册表中定位策略;
///   - 返回的切片有序,sliceNo 从 1 开始递增;
///   - 切片不可能时返回空集合,由调用方处理。
///
/// @author linqibin
/// @since 0.1.0
public interface SlicePlanner {

  /// 返回策略标识符,通常与配置的策略代码对齐
  ///
  /// @return 策略枚举
  SliceStrategy code();

  /// 使用提供的上下文将规划窗口拆分为有序的切片
  ///
  /// @param context 切片上下文,包含窗口、表达式和配置快照
  /// @return 有序的切片列表;切片不可能时返回空列表
  List<SlicePlan> slice(SlicePlanningContext context);
}
