// patra-learn/src/components/ui.tsx
// 页面共用的基础件（画板 learn.css 的 .l-* 按 Tailwind 落地）：按钮与胶囊的类名、类型 / 难度 / 掌握度徽章、标题文字。
// 不带 "use client"：服务端组件与客户端组件都能用（教学组件 ui.tsx 是客户端模块，服务端取不到它的函数值）
import type { ReactNode } from "react";
import type { Difficulty, EntryType } from "@/lib/content/types";
import { DIFFICULTY_LABELS, LEVEL_LABELS, TYPE_LABELS } from "@/lib/site/labels";
import { segmentInline } from "@/teaching/logic";

/// 拼接 className，跳过空值
export function cx(...parts: (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(" ");
}

const BUTTON =
  "inline-flex h-11 items-center justify-center gap-2 whitespace-nowrap rounded-full border px-5 font-ui text-[15px] font-medium transition-[background-color,border-color,transform] duration-160 ease-out active:scale-97 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-mark";

/// 按钮类名：normal 白底、primary 水青实心、quiet 无框文字；sm 矮一号
export function button(kind: "normal" | "primary" | "quiet" = "normal", size: "md" | "sm" = "md") {
  return cx(
    BUTTON,
    size === "sm" && "h-9 px-3.5 text-sm",
    kind === "normal" && "border-line bg-surface text-ink hover:border-ink-5",
    kind === "primary" &&
      "border-teal bg-teal text-white hover:border-teal-deep hover:bg-teal-deep",
    kind === "quiet" && "border-transparent bg-transparent px-3 text-teal hover:bg-teal-wash",
  );
}

/// 胶囊（标签、路线切换、常搜词）；wrap 时文字可折行（放长标题）
export function pill(on = false, wrap = false) {
  return cx(
    "inline-flex items-center gap-1.5 rounded-full border px-3.5 font-ui text-[13.5px] transition-[border-color,background-color] duration-160 ease-out hover:border-teal-mark",
    wrap ? "min-h-7 py-0.5" : "h-8 whitespace-nowrap",
    on
      ? "border-teal-mark bg-teal-tint font-medium text-teal-deep"
      : "border-line bg-surface text-ink",
  );
}

/// 卡片：白底描边；link 时悬停浮起
export function cardClass(link = false) {
  return cx(
    "rounded-card border border-line bg-surface shadow-card",
    link &&
      "block text-ink transition-[box-shadow,border-color,transform] duration-160 ease-out hover:-translate-y-0.5 hover:border-ink-5 hover:shadow-lift",
  );
}

export const panelLabel = "font-semibold font-ui text-meta tracking-[0.02em]";
export const meta = "font-ui text-ink-4 text-meta leading-normal";

const TYPE_STYLE: Record<EntryType, string> = {
  concept: "bg-teal-tint text-teal",
  troubleshooting: "bg-amber-bg text-amber",
  decision: "bg-moss text-decision",
  pitfall: "bg-wrong-bg text-wrong",
};

export function TypeBadge({ type }: { type: EntryType }) {
  return (
    <span
      className={cx(
        "inline-flex h-6 items-center whitespace-nowrap rounded-chip px-2.5 font-semibold font-ui text-tiny tracking-[0.04em]",
        TYPE_STYLE[type],
      )}
    >
      {TYPE_LABELS[type]}
    </span>
  );
}

const BAR_HEIGHTS = ["h-1.5", "h-[9px]", "h-3", "h-[15px]"];
const RANK: Record<Difficulty, number> = { intro: 1, basic: 2, advanced: 3, practice: 4 };

/// 难度不用颜色，看四格刻度填了几格
export function LevelBadge({
  difficulty,
  bare = false,
}: {
  difficulty: Difficulty;
  bare?: boolean;
}) {
  const filled = RANK[difficulty];
  return (
    <span className="inline-flex h-6 items-center gap-[7px] whitespace-nowrap font-medium font-ui text-ink-3 text-tiny">
      <span aria-hidden="true" className="inline-flex items-end gap-0.5">
        {BAR_HEIGHTS.map((h, i) => (
          <i
            key={h}
            className={cx("block w-1 rounded-[1px]", h, i < filled ? "bg-ink-3" : "bg-line")}
          />
        ))}
      </span>
      {bare ? (
        <span className="sr-only">{DIFFICULTY_LABELS[difficulty]}</span>
      ) : (
        DIFFICULTY_LABELS[difficulty]
      )}
    </span>
  );
}

export type ShownLevel = 0 | 1 | 2 | 3;

const CHIP_STYLE = [
  "border border-ink-5 border-dashed bg-transparent font-medium text-ink-4",
  "bg-[#eaf2f0] text-teal-deep shadow-[inset_0_0_0_1px_var(--color-teal-pale)]",
  "bg-teal-tint text-teal-deep",
  "bg-teal text-white",
];

const DOT_STYLE = [
  "bg-transparent shadow-[inset_0_0_0_1.5px_var(--color-ink-5)]",
  "bg-teal-pale",
  "bg-teal-soft",
  "bg-teal",
];

export function MasteryDot({ level, className }: { level: ShownLevel; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cx("inline-block size-2 flex-none rounded-full", DOT_STYLE[level], className)}
    />
  );
}

export function levelLabel(level: ShownLevel) {
  return level === 0 ? "没读过" : LEVEL_LABELS[level];
}

/// 掌握度徽章：没读过是虚线框
export function MasteryChip({ level }: { level: ShownLevel }) {
  return (
    <span
      className={cx(
        "inline-flex h-[26px] items-center gap-1.5 whitespace-nowrap rounded-full pr-2.5 pl-2 font-semibold font-ui text-tiny",
        CHIP_STYLE[level],
      )}
    >
      {level > 0 ? <MasteryDot level={level} className={level === 3 ? "bg-white" : ""} /> : null}
      {levelLabel(level)}
    </span>
  );
}

/// 条目标题：反引号渲染成行内代码
export function TitleText({ text }: { text: string }) {
  let offset = 0;
  const parts = segmentInline(text).map((s) => {
    const key = `${offset}`;
    offset += s.text.length + (s.code ? 2 : 0);
    return { ...s, key };
  });
  return (
    <>
      {parts.map((s) =>
        s.code ? (
          <code
            key={s.key}
            className="rounded-[4px] bg-line-soft px-[5px] py-px font-mono text-[0.82em] text-ink"
          >
            {s.text}
          </code>
        ) : (
          <span key={s.key}>{s.text}</span>
        ),
      )}
    </>
  );
}

/// 页面标题文字（<title>）去掉反引号
export function plainTitle(text: string) {
  return text.replaceAll("`", "");
}

export function SectionHead({ title, note }: { title: string; note?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-2">
      <h2 className="m-0 font-bold font-display text-h2 text-ink leading-[1.4]">{title}</h2>
      {note ? <span className={meta}>{note}</span> : null}
    </div>
  );
}

/// 页面容器：最宽 1200，两侧 24（手机 16）
export const wrap = "mx-auto box-border w-full max-w-page px-6 max-sm:px-4";
