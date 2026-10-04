import type { AbstractSection, Author, EvidenceLevel, PaperDetail } from "@/types/portal";

/// 句末标点：以它们收尾的摘要片段视为完整句子。
const SENTENCE_END = /[.!?。！？…」』)）"'”’]$/;

/// 摘要片段的展示文本：后端按码点截断、不带省略号，片段停在半句时补「…」。
export function snippetText(snippet: string): string {
  const text = snippet.trimEnd();
  return SENTENCE_END.test(text) ? text : `${text}…`;
}

export type EvidenceTone = "moss" | "amber" | "slate";

export interface EvidenceView {
  tone: EvidenceTone;
  lit: number;
  label: string;
  en: string;
  derived: boolean;
}

export type AbstractView =
  | { kind: "structured"; sections: AbstractSection[] }
  | { kind: "plain"; text: string }
  | { kind: "empty" };

export interface BylineView {
  shown: Author[];
  extra: number;
}

const EN_LABEL: Record<string, string> = {
  SYSTEMATIC_REVIEW: "Systematic review",
  RANDOMIZED_CONTROLLED_TRIAL: "RCT",
  COHORT_OR_CASE_CONTROL: "Cohort / Case-control",
  NON_SYSTEMATIC_REVIEW: "Review / Clinical study",
  CASE_REPORT: "Case report",
  UNKNOWN: "Undetermined",
};

/** rank 分档色温：≥4 moss（高强度）/ ≥2 amber（中）/ 其余 slate（低）。未分级（rank 0）不出徽章，色温无意义，归入 slate。 */
function toneOf(rank: number): EvidenceTone {
  if (rank >= 4) return "moss";
  if (rank >= 2) return "amber";
  return "slate";
}

/** 证据等级 → 徽章视图：色温 + 阶梯点亮数（clamp 到 [0,5]）+ 英文标签。 */
export function deriveEvidence(ev: EvidenceLevel): EvidenceView {
  const rank = Math.min(Math.max(ev.rank, 0), 5);
  return {
    tone: toneOf(rank),
    lit: rank,
    label: ev.label,
    en: EN_LABEL[ev.level] ?? "Undetermined",
    derived: ev.derived,
  };
}

/** 摘要三态：结构化段落 / 纯文本 / 空。 */
export function deriveAbstract(p: PaperDetail): AbstractView {
  if (p.abstractSections.length > 0) {
    return { kind: "structured", sections: p.abstractSections };
  }
  if (p.abstractPlainText) {
    return { kind: "plain", text: p.abstractPlainText };
  }
  return { kind: "empty" };
}

export interface FullTextView {
  href: string | null;
  label: string;
}

/** 仅放行 http/https，挡掉 javascript:/data: 等脏协议与非法 URL。 */
function isHttpUrl(url: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === "http:" || u.protocol === "https:";
  } catch {
    return false;
  }
}

/**
 * 全文链接 + 按钮文案。优先级：fullTextUrl（校验 http/https）→ doi.org → PubMed → 无。
 * 文案按实际跳转来源派生，避免「显示 DOI 实际跳 PubMed」的误导。
 */
export function deriveFullText(p: PaperDetail): FullTextView {
  if (p.fullTextUrl && isHttpUrl(p.fullTextUrl)) {
    return { href: p.fullTextUrl, label: `去全文 · ${p.isOa ? "OA" : "全文"}` };
  }
  if (p.doi) {
    return { href: `https://doi.org/${p.doi}`, label: "去全文 · DOI" };
  }
  if (p.pmid) {
    return { href: `https://pubmed.ncbi.nlm.nih.gov/${p.pmid}`, label: "去全文 · PubMed" };
  }
  return { href: null, label: "全文链接不可用" };
}

/** byline：前 3 位作者 + 溢出数。 */
export function deriveByline(authors: Author[]): BylineView {
  const shown = authors.slice(0, 3);
  return { shown, extra: Math.max(0, authors.length - shown.length) };
}
