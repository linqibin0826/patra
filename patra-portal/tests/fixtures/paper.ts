import type { Paper } from "@/types/portal";

/** 构造测试用 Paper：默认值贴近真实 search 响应（见设计简报 §3.1），按需覆盖。 */
export function makePaper(overrides: Partial<Paper> = {}): Paper {
  return {
    id: "352303128713027974",
    title: "OSBPL6 protects against demyelination and behavioral disorder.",
    journal: "Journal of advanced research",
    year: 2026,
    authors: ["Chang Mengni", "Zhang Kaiqi", "Li Ye"],
    cites: 0,
    bookmarks: 0,
    doi: "10.1016/j.jare.2026.01.066",
    pmid: "41605285",
    source: "PubMed",
    aiSummary: null,
    estimatedReadMin: null,
    kind: "Journal Article",
    minutesAgo: null,
    venueId: "319041872872550658",
    evidenceLevel: { level: "UNKNOWN", rank: 0, label: "未分级", derived: false },
    abstractSnippet: "INTRODUCTION: Demyelination is associated with behavioral disorder.",
    ...overrides,
  };
}
