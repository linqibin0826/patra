import { describe, expect, it } from "vitest";
import { shortTitle, splitTitle } from "@/lib/portal-api/title-derive";

const PMID_41575322 =
  "Explainable machine learning for early diagnosis of esophageal cancer: A feature-enriched Light Gradient Boosting Machine framework with Shapley Additive Explanations and Local Interpretable Model-Agnostic Explanations interpretations.";

describe("splitTitle", () => {
  it("在冒号处拆成主标题与副标题，去掉末尾句点", () => {
    const parts = splitTitle(PMID_41575322);
    expect(parts.main).toBe(
      "Explainable machine learning for early diagnosis of esophageal cancer",
    );
    expect(parts.sub).toMatch(/^A feature-enriched/);
    expect(parts.sub).toMatch(/interpretations$/);
    expect(parts.size).toBe("lg");
  });

  it("没有冒号时只去末尾句点", () => {
    expect(splitTitle("Global trends in esophageal cancer.")).toEqual({
      main: "Global trends in esophageal cancer",
      sub: null,
      size: "lg",
    });
  });

  it("冒号前不足两个词（Re: 这类）不拆", () => {
    expect(splitTitle("Re: a letter about trials").sub).toBeNull();
  });

  it("末尾的问号保留", () => {
    expect(splitTitle("Is screening worth it?").main).toBe("Is screening worth it?");
  });

  it("全角冒号也能拆", () => {
    expect(splitTitle("食管癌的早期诊断研究：一项机器学习框架")).toMatchObject({
      main: "食管癌的早期诊断研究",
      sub: "一项机器学习框架",
    });
  });

  it("没有冒号且超过 120 字符 → md；正好 120 字符 → lg", () => {
    const long = `${"a".repeat(60)} ${"b".repeat(60)}`; // 121
    expect(long).toHaveLength(121);
    expect(splitTitle(long).size).toBe("md");
    expect(splitTitle(long.slice(0, 120)).size).toBe("lg");
  });

  it("冒号在标签外时按原字符串切开，保留富文本标签", () => {
    expect(splitTitle("<i>BRCA1</i> and <i>BRCA2</i>: a review of mutations")).toMatchObject({
      main: "<i>BRCA1</i> and <i>BRCA2</i>",
      sub: "a review of mutations",
    });
  });

  it("冒号在标签里面时不拆", () => {
    expect(splitTitle("<i>Homo sapiens: a</i> genome study").sub).toBeNull();
  });
});

describe("shortTitle", () => {
  it("面包屑用的短标题：主标题去掉富文本标签", () => {
    expect(shortTitle(PMID_41575322)).toBe(
      "Explainable machine learning for early diagnosis of esophageal cancer",
    );
    expect(shortTitle("<i>BRCA1</i> carriers: a cohort")).toBe("BRCA1 carriers");
  });
});
