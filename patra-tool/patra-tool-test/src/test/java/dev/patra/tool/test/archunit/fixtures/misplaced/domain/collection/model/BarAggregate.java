package dev.patra.tool.test.archunit.fixtures.misplaced.domain.collection.model;

/// 测试夹具：放错位置的聚合（不在 `model.aggregate` 下），验证放宽后的规则仍会拒绝它。
public class BarAggregate {}
