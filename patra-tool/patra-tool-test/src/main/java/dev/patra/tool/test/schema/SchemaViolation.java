package dev.patra.tool.test.schema;

/// 结构检查发现的一处违规。
///
/// @param section 违反的 `database.md` 章节，形如 `§2`
/// @param table 出问题的表；schema 层面的违规填 schema 名
/// @param message 一句说明，写清现状和应该怎样
public record SchemaViolation(String section, String table, String message) {

  /// 输出成 `[§2] itr_comment：…`，测试失败时一行一条。
  @Override
  public String toString() {
    return "[" + section + "] " + table + "：" + message;
  }
}
