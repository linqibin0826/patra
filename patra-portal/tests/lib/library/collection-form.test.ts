import { describe, expect, it } from "vitest";
import { collectionFormSchema } from "@/lib/library/collection-form";

const issues = (name: string, description = "") => {
  const parsed = collectionFormSchema.safeParse({ name, description });
  return parsed.success ? [] : parsed.error.issues.map((issue) => issue.message);
};

describe("collectionFormSchema", () => {
  it("名称必填（去掉首尾空白后）", () => {
    expect(issues("   ")).toEqual(["请输入名称"]);
  });

  it("名称最多 100 字", () => {
    expect(issues("名".repeat(100))).toEqual([]);
    expect(issues("名".repeat(101))).toEqual(["名称最多 100 个字"]);
  });

  it("counts emoji as one grapheme：emoji 计 1 个字", () => {
    expect(issues("👍🏽".repeat(100))).toEqual([]);
  });

  it("简介最多 500 字", () => {
    expect(issues("组会", "字".repeat(501))).toEqual(["简介最多 500 字"]);
  });
});
