package dev.patra.tool.test.schema;

/// 表的档位，决定它必须带哪些审计列（`database.md` 第 3 节）。
public enum TableTier {
  /// 主表：有自己的生命周期、会被按行修改，「谁改的」有意义，带全套审计列。
  MAIN,
  /// 其他表：只带 `id` 和两个审计时间。
  OTHER
}
