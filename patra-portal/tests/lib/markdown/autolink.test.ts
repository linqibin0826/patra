import { describe, expect, it } from "vitest";
import { trimTrailingPunctuation } from "@/lib/markdown/autolink";

describe("trimTrailingPunctuation", () => {
  it.each([
    ["10.1/abc.", "10.1/abc"],
    ["x），", "x"],
    ["x。", "x"],
    ["x」", "x"],
    ["https://a.b/c)", "https://a.b/c"],
    ["https://a.b/(c)", "https://a.b/(c)"],
    ["https://a.b/c?d=1;", "https://a.b/c?d=1"],
  ])("%s → %s", (input, expected) => {
    expect(trimTrailingPunctuation(input)).toBe(expected);
  });
});
