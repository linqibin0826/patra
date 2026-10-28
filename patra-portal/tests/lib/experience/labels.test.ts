import { describe, expect, it } from "vitest";
import {
  ARTICLE_TYPE_LABELS,
  ARTICLE_TYPES,
  FINAL_OUTCOME_LABELS,
  FINAL_OUTCOMES,
  FIRST_DECISION_LABELS,
  FIRST_DECISIONS,
} from "@/lib/experience/labels";

describe("枚举的中文（PAP-122 交接 3.1）", () => {
  it("首轮结果五种", () => {
    expect(FIRST_DECISIONS.map((value) => FIRST_DECISION_LABELS[value])).toEqual([
      "直接拒稿",
      "审后拒稿",
      "大修",
      "小修",
      "录用",
    ]);
  });

  it("最终结果四种", () => {
    expect(FINAL_OUTCOMES.map((value) => FINAL_OUTCOME_LABELS[value])).toEqual([
      "录用",
      "拒稿",
      "撤稿",
      "进行中",
    ]);
  });

  it("文章类型六种，按表单下拉的顺序", () => {
    expect(ARTICLE_TYPES.map((value) => ARTICLE_TYPE_LABELS[value])).toEqual([
      "原创研究",
      "综述",
      "系统综述 / Meta 分析",
      "病例报告",
      "短篇通讯",
      "其他",
    ]);
  });
});
