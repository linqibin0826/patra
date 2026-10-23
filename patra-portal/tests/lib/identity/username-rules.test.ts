import { describe, expect, it } from "vitest";
import { normalizeUsername, usernameIssue } from "@/lib/identity/username-rules";

describe("usernameIssue", () => {
  it.each([
    ["", "REQUIRED"],
    ["   ", "REQUIRED"],
    ["a b", "INVALID_CHARACTER"],
    ["陈宇", "INVALID_CHARACTER"],
    ["chen.yu", "INVALID_CHARACTER"],
    ["ab", "TOO_SHORT"],
    ["a".repeat(31), "TOO_LONG"],
    ["-abc", "INVALID_FORMAT"],
    ["abc_", "INVALID_FORMAT"],
    ["a__b", "INVALID_FORMAT"],
    ["a-_b", "INVALID_FORMAT"],
    ["12345", "DIGITS_ONLY"],
  ])("%j → %s", (raw, issue) => {
    expect(usernameIssue(raw)).toBe(issue);
  });

  it.each(
    ["abc", "chen-yu", "a_b-c", "a1", "abc".repeat(10), "2026-reader"].filter((v) => v.length >= 3),
  )("%j 合规", (raw) => {
    expect(usernameIssue(raw)).toBeNull();
  });

  it("trims_and_lowercases_before_checking", () => {
    expect(usernameIssue(" Chen-Yu ")).toBeNull();
    expect(normalizeUsername(" Chen-Yu ")).toBe("chen-yu");
  });
});
