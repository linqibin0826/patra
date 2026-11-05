import { describe, expect, it } from "vitest";
import { daysBetween, parseDateInput } from "@/lib/date-input";

describe("parseDateInput：常见写法规范成 YYYY-MM-DD", () => {
  it.each([
    ["2026-08-14", "2026-08-14"],
    ["2026/8/14", "2026-08-14"],
    ["2026.8.14", "2026-08-14"],
    ["20260814", "2026-08-14"],
    ["2026年8月14日", "2026-08-14"],
    [" 2026-8-4 ", "2026-08-04"],
  ])("%s → %s", (text, expected) => {
    expect(parseDateInput(text)).toBe(expected);
  });

  it.each(["2026/9/31", "2026-13-01", "2026-02-29", "abc", "", "   "])("认不出：%s", (text) => {
    expect(parseDateInput(text)).toBeNull();
  });
});

describe("daysBetween", () => {
  it("两个日期相差的天数", () => {
    expect(daysBetween("2026-09-15", "2026-09-28")).toBe(13);
    expect(daysBetween("2025-12-31", "2026-01-01")).toBe(1);
  });
});
