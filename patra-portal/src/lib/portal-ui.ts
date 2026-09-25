/// 详情页 / 状态屏共用的暖纸按钮 class 串（复刻 hi-fi .btn-primary=clay / .btn-sec）。
const BTN_BASE =
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md border px-3.5 py-2 font-sans text-base font-semibold no-underline transition-colors outline-none focus-visible:shadow-(--ring-focus) disabled:cursor-not-allowed";

export const btnPrimary = `${BTN_BASE} border-clay-700 bg-clay-600 text-(--fg-on-clay) hover:bg-clay-700 active:bg-clay-800`;
export const btnSecondary = `${BTN_BASE} border-(--border-default) bg-paper-50 text-(--fg-1) hover:bg-paper-200`;
export const btnBlock = "w-full px-3.5 py-2.5";

/// 数据来源色点 class（PaperCard / PaperListItem 共用）；未知来源回退弱化灰。
const SOURCE_DOT_CLASS: Record<string, string> = {
  PubMed: "bg-emerald-500",
  "Europe PMC": "bg-clay-500",
  Crossref: "bg-sky-500",
};

export function sourceDotClass(source: string): string {
  return SOURCE_DOT_CLASS[source] ?? "bg-(--fg-3)";
}
