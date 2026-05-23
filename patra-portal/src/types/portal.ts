/**
 * Patra portal 领域类型 — 与 handoff `data.jsx` 实际字段对齐
 */

export type ComposerMode = "keyword" | "pmid" | "doi" | "author";
export type FeedTab = "trending" | "recent" | "cited";
export type PaperSource = "PubMed" | "Europe PMC" | "Crossref";
export type TopicHeatTier = 1 | 2 | 3 | 4 | 5;

export interface SearchMode {
  id: ComposerMode;
  label: string;
  placeholder: string;
  mono: boolean;
}

export interface ExampleQuery {
  mode: ComposerMode;
  text: string;
}

export interface PortalStats {
  records: number;
  sources: number;
  lastIngestMin: number;
  todayAdded: number;
  todayDelta: number;
}

export interface Topic {
  term: string;
  heat: number;
  count: number;
  delta?: string;
}

export interface JournalCoverStyle {
  bg: string;
  ink: string;
  rule: string;
}

export interface Journal {
  id: string;
  name: string;
  abbr: string;
  cover: string;
  coverStyle: JournalCoverStyle;
  impact: number;
  weekly: number;
  founded: number;
}

export interface Paper {
  id: string;
  title: string;
  journal: string;
  year: number;
  authors: string[];
  authorCount: number;
  cites: number;
  bookmarks: number;
  readMin: number;
  doi: string;
  pmid: string;
  source: PaperSource;
  ai: string;
  kind: string;
  minutesAgo?: number;
}

export type Feed = Record<FeedTab, Paper[]>;
