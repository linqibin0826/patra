import type { Paper, PaperDetail } from "@/types/portal";

/** 构造测试用 Paper：默认值贴近真实 search 响应（见设计简报 §3.1），按需覆盖。 */
export function makePaper(overrides: Partial<Paper> = {}): Paper {
  return {
    id: "352303128713027974",
    title: "OSBPL6 protects against demyelination and behavioral disorder.",
    journal: "Journal of advanced research",
    year: 2026,
    publishedDate: "2026",
    authors: ["Chang Mengni", "Zhang Kaiqi", "Li Ye"],
    cites: 0,
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

/** 构造测试用 PaperDetail：默认是一篇没有全文、只有 DOI 的文献，按需覆盖。 */
export function makePaperDetail(overrides: Partial<PaperDetail> = {}): PaperDetail {
  return {
    id: "352303012816019646",
    title: "A feasibility RCT of SMS reminders for cervical screening.",
    originalTitle: null,
    venueId: null,
    venueName: "BMJ Open",
    publicationYear: 2026,
    publishedDate: "2026",
    evidenceLevel: { level: "UNKNOWN", rank: 0, label: "未分级", derived: false },
    abstractType: null,
    abstractSections: [],
    abstractPlainText: "Abstract.",
    doi: "10.1136/bmjopen-2026-122934",
    pmid: "42776074",
    pmcid: null,
    pii: null,
    primaryType: null,
    publicationTypes: ["Journal Article"],
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
    source: "PubMed",
    fullTextUrl: null,
    estimatedReadMin: null,
    retracted: false,
    hasFullText: false,
    hasPdf: false,
    pdfSizeBytes: null,
    ...overrides,
  };
}
