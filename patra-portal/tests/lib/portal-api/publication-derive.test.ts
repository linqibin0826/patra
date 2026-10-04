import { describe, expect, it } from "vitest";
import {
  deriveAbstract,
  deriveByline,
  deriveEvidence,
  deriveFullText,
  snippetText,
} from "@/lib/portal-api/publication-derive";
import type { Author, EvidenceLevel, PaperDetail } from "@/types/portal";

function ev(level: string, rank: number, derived: boolean): EvidenceLevel {
  return { level, rank, label: "L", derived };
}
function paper(overrides: Partial<PaperDetail> = {}): PaperDetail {
  return {
    id: "1",
    title: "T",
    originalTitle: null,
    venueId: null,
    venueName: null,
    publicationYear: null,
    evidenceLevel: ev("UNKNOWN", 0, false),
    abstractType: null,
    abstractSections: [],
    abstractPlainText: null,
    doi: null,
    pmid: null,
    pmcid: null,
    pii: null,
    primaryType: null,
    publicationTypes: [],
    citationCount: null,
    numberOfReferences: null,
    conflictOfInterest: null,
    isOa: null,
    oaStatus: null,
    authors: [],
    meshHeadings: [],
    keywords: [],
    funding: [],
    dates: [],
    aiSummary: null,
    source: null,
    fullTextUrl: null,
    bookmarks: null,
    estimatedReadMin: null,
    ...overrides,
  };
}

describe("deriveEvidence", () => {
  it("rank 决定色温与阶梯：5→moss/5，3→amber/3，1 与 0→slate", () => {
    expect(deriveEvidence(ev("SYSTEMATIC_REVIEW", 5, true))).toMatchObject({
      tone: "moss",
      lit: 5,
    });
    expect(deriveEvidence(ev("COHORT_OR_CASE_CONTROL", 3, true))).toMatchObject({
      tone: "amber",
      lit: 3,
    });
    expect(deriveEvidence(ev("CASE_REPORT", 1, true))).toMatchObject({ tone: "slate", lit: 1 });
    // 未分级不出徽章（EvidenceBadge 只渲染 derived），色温无意义：归入最低档，不另设一档用不到的样式
    expect(deriveEvidence(ev("UNKNOWN", 0, false))).toMatchObject({ tone: "slate", lit: 0 });
  });
  it("en 标签按 level 映射，derived 透传", () => {
    const v = deriveEvidence(ev("RANDOMIZED_CONTROLLED_TRIAL", 4, true));
    expect(v.en).toBe("RCT");
    expect(v.derived).toBe(true);
  });
  it("rank 越界（>5）clamp 后 tone 与 lit 一致", () => {
    const v = deriveEvidence(ev("SYSTEMATIC_REVIEW", 6, true));
    expect(v.lit).toBe(5);
    expect(v.tone).toBe("moss");
  });
});

describe("deriveAbstract", () => {
  it("有 sections → structured", () => {
    const v = deriveAbstract(paper({ abstractSections: [{ label: "BACKGROUND", text: "x" }] }));
    expect(v.kind).toBe("structured");
  });
  it("仅 plainText → plain", () => {
    expect(deriveAbstract(paper({ abstractPlainText: "x" })).kind).toBe("plain");
  });
  it("都无 → empty", () => {
    expect(deriveAbstract(paper()).kind).toBe("empty");
  });
});

describe("deriveFullText", () => {
  it("fullTextUrl(http/https) 优先，OA 文案", () => {
    const v = deriveFullText(
      paper({ fullTextUrl: "https://x", doi: "10.1/y", pmid: "9", isOa: true }),
    );
    expect(v).toEqual({ href: "https://x", label: "去全文 · OA" });
  });
  it("fullTextUrl 非 OA → 全文 文案", () => {
    expect(deriveFullText(paper({ fullTextUrl: "https://x", isOa: false })).label).toBe(
      "去全文 · 全文",
    );
  });
  it("非法协议的 fullTextUrl 被丢弃，降级到 doi", () => {
    const v = deriveFullText(paper({ fullTextUrl: "javascript:alert(1)", doi: "10.1/y" }));
    expect(v).toEqual({ href: "https://doi.org/10.1/y", label: "去全文 · DOI" });
  });
  it("无 fullTextUrl → doi.org，DOI 文案", () => {
    expect(deriveFullText(paper({ doi: "10.1/y", pmid: "9" }))).toEqual({
      href: "https://doi.org/10.1/y",
      label: "去全文 · DOI",
    });
  });
  it("仅 pmid → PubMed，PubMed 文案", () => {
    expect(deriveFullText(paper({ pmid: "9" }))).toEqual({
      href: "https://pubmed.ncbi.nlm.nih.gov/9",
      label: "去全文 · PubMed",
    });
  });
  it("都无 → href null，不可用文案", () => {
    expect(deriveFullText(paper())).toEqual({ href: null, label: "全文链接不可用" });
  });
});

describe("deriveByline", () => {
  it("前 3 位 + extra", () => {
    const authors: Author[] = [1, 2, 3, 4, 5].map((n) => ({
      order: n,
      first: n === 1,
      corresponding: false,
      name: `A${n}`,
      affiliation: null,
    }));
    const { shown, extra } = deriveByline(authors);
    expect(shown).toHaveLength(3);
    expect(extra).toBe(2);
  });
  it("≤3 位作者时 extra 为 0", () => {
    const authors: Author[] = [1, 2].map((n) => ({
      order: n,
      first: n === 1,
      corresponding: false,
      name: `A${n}`,
      affiliation: null,
    }));
    expect(deriveByline(authors).extra).toBe(0);
    expect(deriveByline([]).extra).toBe(0);
  });
});

describe("snippetText", () => {
  it("后端截断的摘要片段（不以句末标点收尾）补省略号", () => {
    expect(snippetText("Here, we demonst")).toBe("Here, we demonst…");
    expect(snippetText("…ends with space ")).toBe("…ends with space…");
  });

  it("完整句子原样返回", () => {
    expect(snippetText("We found no difference.")).toBe("We found no difference.");
    expect(snippetText("结论明确。")).toBe("结论明确。");
    expect(snippetText("Was it effective?")).toBe("Was it effective?");
    expect(snippetText("已经截断…")).toBe("已经截断…");
  });
});
