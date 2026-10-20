import { describe, expect, it } from "vitest";
import { inlinePlainText, shortTitle, splitTitle } from "@/lib/portal-api/title-derive";

const PMID_41575322 =
  "Explainable machine learning for early diagnosis of esophageal cancer: A feature-enriched Light Gradient Boosting Machine framework with Shapley Additive Explanations and Local Interpretable Model-Agnostic Explanations interpretations.";

/** 拆分结果的纯文本形态，便于断言。 */
function plain(title: string) {
  const parts = splitTitle(title);
  return {
    main: inlinePlainText(parts.main),
    sub: parts.sub && inlinePlainText(parts.sub),
    size: parts.size,
  };
}

describe("splitTitle", () => {
  it("在冒号处拆成主标题与副标题，去掉末尾句点", () => {
    const parts = plain(PMID_41575322);
    expect(parts.main).toBe(
      "Explainable machine learning for early diagnosis of esophageal cancer",
    );
    expect(parts.sub).toMatch(/^A feature-enriched/);
    expect(parts.sub).toMatch(/interpretations$/);
    expect(parts.size).toBe("lg");
  });

  it("没有冒号时只去末尾句点", () => {
    expect(plain("Global trends in esophageal cancer.")).toEqual({
      main: "Global trends in esophageal cancer",
      sub: null,
      size: "lg",
    });
  });

  it("冒号前只有一个西文单词（Re: 这类）不拆", () => {
    expect(plain("Re: a letter about trials").sub).toBeNull();
  });

  it("末尾的问号保留", () => {
    expect(plain("Is screening worth it?").main).toBe("Is screening worth it?");
  });

  it("全角冒号也能拆", () => {
    expect(plain("食管癌的早期诊断研究：一项机器学习框架")).toMatchObject({
      main: "食管癌的早期诊断研究",
      sub: "一项机器学习框架",
    });
  });

  it("没有冒号且超过 120 字符 → md；正好 120 字符 → lg", () => {
    const long = `${"a".repeat(60)} ${"b".repeat(60)}`; // 121
    expect(long).toHaveLength(121);
    expect(plain(long).size).toBe("md");
    expect(plain(long.slice(0, 120)).size).toBe("lg");
  });

  it("冒号在标签外时切开，富文本元素留在主标题里", () => {
    const parts = splitTitle("<i>BRCA1</i> and <i>BRCA2</i>: a review of mutations");
    expect(inlinePlainText(parts.main)).toBe("BRCA1 and BRCA2");
    expect(parts.main.filter((n) => n.kind === "element")).toHaveLength(2);
    expect(parts.sub && inlinePlainText(parts.sub)).toBe("a review of mutations");
  });

  it("冒号在标签里面时不拆", () => {
    expect(plain("<i>Homo sapiens: a</i> genome study").sub).toBeNull();
  });

  it("标题里的字面尖括号照原文保留，不影响找冒号（age < 5 versus age > 10）", () => {
    const parts = plain("Outcomes in age < 5 versus age > 10: a cohort study");
    expect(parts.main).toBe("Outcomes in age < 5 versus age > 10");
    expect(parts.sub).toBe("a cohort study");
  });

  it("整句方括号包住的译名标题不拆（PubMed 非英文文献）", () => {
    expect(plain("[Analysis of X: a report of 12 cases].")).toEqual({
      main: "[Analysis of X: a report of 12 cases]",
      sub: null,
      size: "lg",
    });
  });
});

describe("shortTitle", () => {
  it("面包屑用的短标题：主标题的纯文本", () => {
    expect(shortTitle(PMID_41575322)).toBe(
      "Explainable machine learning for early diagnosis of esophageal cancer",
    );
    expect(shortTitle("<i>BRCA1</i> carriers: a cohort")).toBe("BRCA1 carriers");
    expect(shortTitle("Outcomes in age < 5 years")).toBe("Outcomes in age < 5 years");
  });
});
