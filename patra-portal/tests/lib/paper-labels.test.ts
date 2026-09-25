import { describe, expect, it } from "vitest";
import {
  EVIDENCE_LABELS,
  languageFacetLabel,
  languageName,
  typeFacetLabel,
  typeName,
} from "@/lib/paper-labels";

describe("EVIDENCE_LABELS", () => {
  it("覆盖 6 档证据等级，文案与详情页徽章一致", () => {
    expect(EVIDENCE_LABELS).toEqual({
      SYSTEMATIC_REVIEW: "系统综述 / Meta 分析",
      RANDOMIZED_CONTROLLED_TRIAL: "随机对照试验",
      COHORT_OR_CASE_CONTROL: "队列 / 病例对照",
      NON_SYSTEMATIC_REVIEW: "非系统综述 / 临床研究",
      CASE_REPORT: "病例报告",
      UNKNOWN: "未分级",
    });
  });
});

describe("文献类型中文名", () => {
  it("typeName：收录的类型返回中文名", () => {
    expect(typeName("Review")).toBe("综述");
    expect(typeName("Clinical Trial, Phase III")).toBe("III 期临床试验");
  });

  it("typeName：大小写不敏感（与 BE 一致）", () => {
    expect(typeName("review")).toBe("综述");
  });

  it("typeName：未收录的类型回退原值", () => {
    expect(typeName("Some New Type")).toBe("Some New Type");
  });

  it("typeFacetLabel：中文名 · 英文名，RCT 用短名", () => {
    expect(typeFacetLabel("Review")).toBe("综述 · Review");
    expect(typeFacetLabel("Randomized Controlled Trial")).toBe("随机对照试验 · RCT");
    expect(typeFacetLabel("Some New Type")).toBe("Some New Type");
  });
});

describe("语言中文名", () => {
  it("languageName：收录的语言返回中文名，大小写不敏感", () => {
    expect(languageName("en")).toBe("英文");
    expect(languageName("ZH")).toBe("中文");
  });

  it("languageName：未收录回退原值", () => {
    expect(languageName("xx")).toBe("xx");
  });

  it("languageFacetLabel：中文名 · 代码", () => {
    expect(languageFacetLabel("de")).toBe("德文 · de");
    expect(languageFacetLabel("xx")).toBe("xx");
  });
});
