/// 详情页 / 状态屏共用的暖纸按钮 class 串（.btn-primary=clay / .btn-sec）。
/// 末尾图标（箭头 / 外链，标 `data-icon="trailing"`）hover 时向前微移，按下时整体下沉 1px；
/// 前置图标不动。禁用态不响应 hover / 按下。
const BTN_BASE =
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-full border px-5 py-2.5 font-sans text-base font-semibold no-underline outline-none transition duration-200 ease-out-expo disabled:cursor-not-allowed motion-safe:[&>svg]:transition-transform [&>svg]:duration-300 [&>svg]:ease-out-expo motion-safe:not-disabled:hover:[&>svg[data-icon=trailing]]:translate-x-0.5 motion-safe:not-disabled:active:translate-y-px";

export const btnPrimary = `${BTN_BASE} border-action-primary bg-action-primary text-fg-on-clay not-disabled:hover:border-action-primary-hover not-disabled:hover:bg-action-primary-hover not-disabled:active:bg-action-primary-press`;
export const btnSecondary = `${BTN_BASE} border-border-default bg-paper-50 text-fg-1 not-disabled:hover:border-border-hover not-disabled:hover:bg-paper-200`;
export const btnBlock = "w-full px-4 py-3";

/// 自绘复选框：纸面方框，选中为 action-primary 填充 + 纸色勾线（焦点环走全局规则）。
/// hover 只加深未选中控件的描边：选中态的 action-primary 描边不被 hover 盖掉。
const CHECK_BASE =
  "size-4 shrink-0 cursor-pointer appearance-none border border-border-strong bg-bg-elevated bg-center bg-no-repeat [background-size:80%] transition-colors duration-150 not-checked:hover:border-ink-500 checked:border-action-primary checked:bg-action-primary disabled:cursor-not-allowed disabled:opacity-50";
export const CHECK_INPUT = `${CHECK_BASE} rounded-xs checked:bg-(image:--check-mark)`;
/// 自绘单选：圆形，选中为填充 + 纸色圆点。
export const RADIO_INPUT = `${CHECK_BASE} rounded-full checked:bg-(image:--radio-dot)`;
/// 拨动开关（语义仍是 checkbox）：白色旋钮以背景图绘制，选中时滑到右端、底色变 action-primary。
export const SWITCH_INPUT =
  "h-[18px] w-8 shrink-0 cursor-pointer appearance-none rounded-full bg-paper-400 bg-(image:--switch-knob) bg-no-repeat [background-position:2px_center] [background-size:14px_14px] transition-colors motion-safe:transition-[background-color,background-position] duration-200 ease-out-expo checked:bg-action-primary checked:[background-position:calc(100%-2px)_center]";

/// 数据来源色点 class（PaperCard / PaperListItem 共用）；未知来源回退 `source-other`。
const SOURCE_DOT_CLASS: Record<string, string> = {
  PubMed: "bg-source-pubmed",
  "Europe PMC": "bg-source-epmc",
  Crossref: "bg-source-crossref",
};

export function sourceDotClass(source: string): string {
  return SOURCE_DOT_CLASS[source] ?? "bg-source-other";
}
