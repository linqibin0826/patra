import { describe, expect, it } from "vitest";
import { graphemeCount, graphemes } from "@/lib/grapheme";

describe("grapheme", () => {
  it("counts_zwj_emoji_as_one_grapheme", () => {
    expect(graphemeCount("👨‍👩‍👧a")).toBe(2);
  });

  it("汉字、组合字符各算一个", () => {
    expect(graphemeCount("陈宇")).toBe(2);
    expect(graphemes("é")).toEqual(["é"]);
  });

  it("空串是 0", () => {
    expect(graphemeCount("")).toBe(0);
  });
});
