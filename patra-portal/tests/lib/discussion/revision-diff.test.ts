import { describe, expect, it } from "vitest";
import { diffVersions, resolveMentionMarkers } from "@/lib/discussion/revision-diff";

describe("diffVersions", () => {
  it("中文按词标出新增", () => {
    const parts = diffVersions("样本量太小", "样本量明显太小");
    expect(parts).toContainEqual({ kind: "added", text: "明显" });
    expect(
      parts
        .filter((part) => part.kind !== "added")
        .map((part) => part.text)
        .join(""),
    ).toBe("样本量太小");
  });

  it("英文按单词标出删除与新增", () => {
    const parts = diffVersions("a small cohort", "a large cohort");
    expect(parts).toContainEqual({ kind: "removed", text: "small" });
    expect(parts).toContainEqual({ kind: "added", text: "large" });
  });

  it("相同的内容只有 same", () => {
    expect(diffVersions("同一段", "同一段").every((part) => part.kind === "same")).toBe(true);
  });
});

describe("resolveMentionMarkers", () => {
  const mentions = [
    { userId: "42", username: "zhou-min", displayName: "周敏", deleted: false },
    { userId: "43", username: null, displayName: "王立", deleted: false },
    { userId: "44", username: null, displayName: null, deleted: true },
  ];

  it("标记换成 @用户名 / @昵称 / @已注销用户；转义与行内代码里的不换", () => {
    expect(resolveMentionMarkers("同意 <@42>、<@43>、<@44> 与 \\<@42>，`<@42>`", mentions)).toBe(
      "同意 @zhou-min、@王立、@已注销用户 与 \\<@42>，`<@42>`",
    );
  });
});
