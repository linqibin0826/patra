import { describe, expect, it } from "vitest";
import { formatCount } from "@/lib/format-count";

describe("formatCount", () => {
  it("0 不写数字", () => {
    expect(formatCount(0)).toBe("");
  });

  it("小数字原样", () => {
    expect(formatCount(3)).toBe("3");
  });

  it("千分位，不缩写", () => {
    expect(formatCount(1204)).toBe("1,204");
    expect(formatCount(1000000)).toBe("1,000,000");
  });
});
