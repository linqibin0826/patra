import { describe, expect, it } from "vitest";
import { findMentionQuery, replaceMention, shouldAutoClose } from "@/lib/discussion/mention-query";

/// `|` 标出光标位置
function query(marked: string) {
  const caret = marked.indexOf("|");
  return findMentionQuery(marked.replace("|", ""), caret);
}

describe("findMentionQuery（简报 §3.6 触发规则）", () => {
  it.each([
    ["hi @ch|", { start: 3, query: "ch" }],
    ["@|", { start: 0, query: "" }],
    ["(@zh|", { start: 1, query: "zh" }],
    ["说@|", null],
    ["看，@陈|", { start: 2, query: "陈" }],
    ["a@ch|", null],
    ["@ch en|", null],
    ["@ch |", null],
    ["@ch,|", null],
    ["@chen-yu_|", { start: 0, query: "chen-yu_" }],
  ])("%s", (marked, expected) => {
    expect(query(marked)).toEqual(expected);
  });
});

describe("replaceMention", () => {
  it("把查询词换成 @用户名 加一个空格，光标在空格后", () => {
    expect(replaceMention("请 @zh 看", { start: 2, query: "zh" }, "zhou-min")).toEqual({
      value: "请 @zhou-min  看",
      caret: 12,
    });
  });
});

describe("shouldAutoClose", () => {
  it.each([
    ["abcdefg", 0, true],
    ["abcdef", 0, false],
    ["abcdefg", 1, false],
    ["陈宇陈宇陈宇陈", 0, true],
  ] as const)("%s / %i → %s", (q, count, expected) => {
    expect(shouldAutoClose(q, count)).toBe(expected);
  });
});
