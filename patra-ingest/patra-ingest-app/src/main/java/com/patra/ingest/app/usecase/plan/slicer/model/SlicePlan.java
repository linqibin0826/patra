package com.patra.ingest.app.usecase.plan.slicer.model;

import com.patra.expr.Expr;
import java.util.Objects;

/// 切片规划结果(应用层·DTO)
/// 
/// 由切片策略生成的中间表示,尚未持久化。包含:
/// 
/// - 序号:维护生成顺序和下游任务排序(从 1 开始;不允许 0 或负数)
///   - 签名种子:用于派生幂等签名的规范化 JSON
///   - 窗口规格 JSON:窗口边界参数(可能与种子不同;前者用于幂等性,后者用于执行语义)
///   - 表达式:过滤/约束切片数据的已编译表达式(Expr)
/// 
/// #### 不变式
/// 
/// - `sliceNo >= 1`
///   - `sliceSignatureSeed != null && !sliceSignatureSeed.isBlank()`
///   - `windowSpecJson != null && !windowSpecJson.isBlank()`
///   - `sliceExpr != null`
/// 
/// #### 线程安全
/// 
/// Record 不可变,可在线程间安全共享。
/// 
/// #### 下游处理
/// 
/// 将被转换为领域层 Slice 聚合或直接作为任务参数分发。签名种子参与 SHA-256(或类似算法)以派生幂等键。
/// 
/// @param sliceNo 切片序号(从 1 开始)
/// @param sliceSignatureSeed 用于计算切片签名的规范化 JSON 种子
/// @param windowSpecJson 描述窗口边界的规范化 JSON
/// @param sliceExpr 此切片的已编译表达式
/// @author linqibin
/// @since 0.1.0
public record SlicePlan(
    int sliceNo, String sliceSignatureSeed, String windowSpecJson, Expr sliceExpr) {
  public SlicePlan {
    // 验证关键字段以确保下游可以访问所需信息
    Objects.requireNonNull(sliceSignatureSeed, "sliceSignatureSeed 不能为 null");
    Objects.requireNonNull(windowSpecJson, "windowSpecJson 不能为 null");
    Objects.requireNonNull(sliceExpr, "sliceExpr 不能为 null");
  }
}
