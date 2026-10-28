import { describe, expect, it } from "vitest";
import {
  COMMENT_LIMITS,
  limitState,
  measure,
  mentionCandidates,
  normalizeForCount,
} from "@/lib/discussion/limits";

describe("measure", () => {
  it("按字素与 UTF-8 字节计", () => {
    expect(measure("👨‍👩‍👧汉")).toEqual({ graphemes: 2, bytes: 21 });
  });

  it("先统一换行、去首尾空白再计", () => {
    expect(normalizeForCount("  a\r\nb\r ")).toBe("a\nb");
    expect(measure("  a\r\n ").graphemes).toBe(1);
  });
});

describe("limitState", () => {
  const limits = { graphemes: COMMENT_LIMITS.graphemes, bytes: COMMENT_LIMITS.bytes };
  it.each([
    [{ graphemes: 3999, bytes: 3999 }, "ok"],
    [{ graphemes: 4000, bytes: 4000 }, "near"],
    [{ graphemes: 5001, bytes: 5001 }, "over-graphemes"],
    [{ graphemes: 5000, bytes: 20001 }, "over-bytes"],
    [{ graphemes: 5001, bytes: 20001 }, "over-graphemes"],
  ])("%o → %s", (m, state) => {
    expect(limitState(m, limits)).toBe(state);
  });
});

describe("mentionCandidates（后端 5.4.1 的候选语法）", () => {
  it.each([
    ["@chen-yu 和 @Chen-Yu", ["chen-yu"]],
    ["a@b.com", []],
    ["你好@chen", []],
    ["(@chen-yu)", ["chen-yu"]],
    ["@chen-", ["chen"]],
    ["@12345", []],
    ["@ab", []],
    ["`@chen-yu`", []],
    ["[x](https://a/@chen)", []],
    ["https://a/@chen", []],
    ["@a1 @b22 @c333", ["b22", "c333"]],
  ])("%s → %o", (text, expected) => {
    expect(mentionCandidates(text)).toEqual(expected);
  });
});
