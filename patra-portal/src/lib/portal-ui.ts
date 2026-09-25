/// 详情页 / 状态屏共用的暖纸按钮 class 串（复刻 hi-fi .btn-primary=clay / .btn-sec）。
const BTN_BASE =
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md border px-3.5 py-2 font-sans text-base font-semibold no-underline transition-colors outline-none disabled:cursor-not-allowed";

export const btnPrimary = `${BTN_BASE} border-action-primary bg-action-primary text-fg-on-clay hover:border-action-primary-hover hover:bg-action-primary-hover active:bg-action-primary-press`;
export const btnSecondary = `${BTN_BASE} border-border-default bg-paper-50 text-fg-1 hover:bg-paper-200`;
export const btnBlock = "w-full px-3.5 py-2.5";

/// 数据来源色点 class（PaperCard / PaperListItem 共用）；未知来源回退 `source-other`。
const SOURCE_DOT_CLASS: Record<string, string> = {
  PubMed: "bg-source-pubmed",
  "Europe PMC": "bg-source-epmc",
  Crossref: "bg-source-crossref",
};

export function sourceDotClass(source: string): string {
  return SOURCE_DOT_CLASS[source] ?? "bg-source-other";
}
