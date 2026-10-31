// patra-learn/src/teaching/ui.tsx
// 教学组件共用的基础件：组件外壳、按钮、行内文字、选项、判定条、说明条、步骤点、结果徽章（画板 learn.css 的 .l-* 按 Tailwind 落地）
"use client";

import type { CSSProperties, ReactNode } from "react";
import { segmentInline } from "./logic";
import { rise, shake, useMotionOnChange } from "./motion";

/// 拼接 className，跳过空值
export function cx(...parts: (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(" ");
}

export const LETTERS = ["A", "B", "C", "D"] as const;

export type Letter = (typeof LETTERS)[number];

/// 字母转下标
export function letterIndex(letter: Letter) {
  return LETTERS.indexOf(letter);
}

/// 给一组可能重复的文字生成稳定的 key：同样的文字按出现次序加序号
export function occurrenceKeys(texts: readonly string[]): string[] {
  const seen = new Map<string, number>();
  return texts.map((t) => {
    const n = (seen.get(t) ?? 0) + 1;
    seen.set(t, n);
    return `${t}#${n}`;
  });
}

/// 作者写的字符串：反引号渲染成行内代码，其余原样
export function Inline({ text }: { text: string }) {
  const segments = segmentInline(text);
  const keys = occurrenceKeys(segments.map((s) => `${s.code ? "c" : "t"}:${s.text}`));
  return (
    <>
      {segments.map((s, i) =>
        s.code ? (
          <code
            key={keys[i]}
            className="rounded-[4px] bg-line-soft px-[5px] py-px font-mono text-[0.86em] text-ink"
          >
            {s.text}
          </code>
        ) : (
          <span key={keys[i]}>{s.text}</span>
        ),
      )}
    </>
  );
}

const BUTTON = {
  base: "inline-flex h-9 items-center justify-center gap-2 whitespace-nowrap rounded-full border px-3.5 font-ui text-sm font-medium transition-[background-color,border-color,transform] duration-160 ease-out active:scale-97 disabled:cursor-not-allowed disabled:opacity-45 disabled:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-mark",
  normal: "border-line bg-surface text-ink hover:border-ink-5",
  primary: "border-teal bg-teal text-white hover:border-teal-deep hover:bg-teal-deep",
  quiet: "border-transparent bg-transparent px-3 text-teal hover:bg-teal-wash",
} as const;

/// 按钮样式：normal 白底、primary 水青实心、quiet 无框文字按钮
export function buttonClass(kind: "normal" | "primary" | "quiet" = "normal") {
  return cx(BUTTON.base, BUTTON[kind]);
}

export function Button({
  kind = "normal",
  className,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { kind?: "normal" | "primary" | "quiet" }) {
  return <button type="button" className={cx(buttonClass(kind), className)} {...props} />;
}

/// 组件外壳：白卡片、上方标题 + 类型、可选的底部操作栏
export function Widget({
  title,
  kind,
  label,
  children,
  foot,
  bodyClassName,
}: {
  title?: ReactNode;
  kind?: ReactNode;
  label: string;
  children: ReactNode;
  foot?: ReactNode;
  bodyClassName?: string;
}) {
  return (
    <section
      aria-label={label}
      className="overflow-hidden rounded-card border border-line bg-surface font-body text-ink shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-line-soft border-b px-4.5 py-3">
        {title ? (
          <span className="font-bold font-display text-[16.5px] text-ink">{title}</span>
        ) : null}
        {kind ? (
          <span className="inline-flex items-center gap-1.5 font-semibold font-ui text-ink-4 text-tiny tracking-[0.04em]">
            {kind}
          </span>
        ) : null}
      </div>
      <div className={cx("px-5.5 py-5", bodyClassName ?? "flex flex-col gap-4")}>{children}</div>
      {foot ? (
        <div className="flex flex-wrap items-center justify-between gap-3 border-line-soft border-t bg-surface-soft px-4.5 py-3">
          {foot}
        </div>
      ) : null}
    </section>
  );
}

/// 挂载与 motionKey 变化时从下方浮入
export function Reveal({
  motionKey,
  className,
  style,
  children,
}: {
  motionKey?: unknown;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const ref = useMotionOnChange<HTMLDivElement>(rise, motionKey);
  return (
    <div ref={ref} className={className} style={style}>
      {children}
    </div>
  );
}

export type OptionState = "idle" | "picked" | "right" | "wrong" | "dim";

const OPTION_CLASS: Record<OptionState, string> = {
  idle: "border-line bg-surface hover:border-teal-mark cursor-pointer",
  picked: "border-teal-mark bg-teal-wash cursor-pointer",
  right: "border-correct bg-correct-bg",
  wrong: "border-wrong bg-wrong-bg",
  dim: "border-line bg-surface opacity-55",
};

const KEY_CLASS: Record<OptionState, string> = {
  idle: "border-line text-ink-3",
  picked: "border-teal bg-teal text-white",
  right: "border-correct bg-correct text-white",
  wrong: "border-wrong bg-wrong text-white",
  dim: "border-line text-ink-3",
};

/// 一组单选项：作答前 idle / picked，作答后正确项 right、选错的 wrong、其余 dim
export function OptionList({
  options,
  states,
  disabled,
  onPick,
}: {
  options: readonly string[];
  states: OptionState[];
  disabled: boolean;
  onPick: (index: number) => void;
}) {
  return (
    <div className="flex flex-col gap-2.5">
      {options.map((text, i) => {
        const state = states[i] ?? "idle";
        return (
          <button
            key={text}
            type="button"
            aria-pressed={state === "picked"}
            data-state={state}
            disabled={disabled}
            onClick={() => onPick(i)}
            className={cx(
              "box-border flex min-h-12 w-full items-start gap-3 rounded-inner border px-3.5 py-3 text-left font-body text-[15px] text-ink leading-[1.6] transition-[border-color,background-color,opacity] duration-160 ease-out disabled:cursor-default",
              OPTION_CLASS[state],
            )}
          >
            <span
              className={cx(
                "mt-px inline-flex size-6 flex-none items-center justify-center rounded-full border font-semibold font-ui text-tiny",
                KEY_CLASS[state],
              )}
            >
              {LETTERS[i]}
            </span>
            <span>
              <Inline text={text} />
            </span>
          </button>
        );
      })}
    </div>
  );
}

/// 作答后各选项的状态
export function answeredStates(
  count: number,
  answer: number,
  picked: number | null,
): OptionState[] {
  return Array.from({ length: count }, (_, i) =>
    i === answer ? "right" : i === picked ? "wrong" : "dim",
  );
}

/// 判定条：ok 绿底、bad 红底，从下方浮入
export function Verdict({
  tone,
  head,
  children,
  motionKey,
}: {
  tone: "ok" | "bad";
  head?: ReactNode;
  children: ReactNode;
  motionKey?: unknown;
}) {
  return (
    <Reveal
      motionKey={motionKey}
      className={cx(
        "flex items-start gap-2.5 rounded-inner px-4 py-3.5 text-[15px] text-ink leading-[1.7]",
        tone === "ok" ? "bg-correct-bg" : "bg-wrong-bg",
      )}
    >
      <span data-tone={tone}>
        {head ? (
          <b className={cx("font-ui", tone === "ok" ? "text-correct" : "text-wrong")}>{head}</b>
        ) : null}
        {children}
      </span>
    </Reveal>
  );
}

/// 说明条：图示下方的一句说明；内容变化时浮入，tone 为 bad 时变红并抖动
export function Caption({
  lead,
  children,
  tone = "normal",
  motionKey,
}: {
  lead?: ReactNode;
  children: ReactNode;
  tone?: "normal" | "bad";
  motionKey?: unknown;
}) {
  const ref = useMotionOnChange<HTMLDivElement>(tone === "bad" ? shake : rise, motionKey);
  return (
    <div
      ref={ref}
      data-testid="caption"
      data-tone={tone}
      className={cx(
        "box-border flex min-h-12 items-start gap-2.5 rounded-inner px-3.5 py-3 text-[15px] text-ink-2 leading-[1.7] transition-colors duration-280",
        tone === "bad" ? "bg-wrong-bg" : "bg-teal-wash",
      )}
    >
      {lead !== undefined ? (
        <span
          className={cx(
            "min-w-6 flex-none font-semibold font-ui tabular-nums",
            tone === "bad" ? "text-wrong" : "text-teal",
          )}
        >
          {lead}
        </span>
      ) : null}
      <span>{children}</span>
    </div>
  );
}

/// 步骤点：走过的浅水青，当前的加长
export function StepDots({ total, current }: { total: number; current: number }) {
  return (
    <span className="flex items-center gap-1.5" aria-hidden="true">
      {Array.from({ length: total }, (_, i) => (
        <i
          // biome-ignore lint/suspicious/noArrayIndexKey: 步骤点按位置
          key={i}
          className={cx(
            "block h-1.5 rounded-[3px] transition-[background-color,width] duration-280 ease-out",
            i < current
              ? "w-[22px] bg-teal-soft"
              : i === current
                ? "w-[34px] bg-teal"
                : "w-[22px] bg-line",
          )}
        />
      ))}
    </span>
  );
}

export type Tone = "ok" | "bad" | "wait";

const CHIP_CLASS: Record<Tone | "neutral", string> = {
  ok: "bg-correct-bg text-correct",
  bad: "bg-wrong-bg text-wrong",
  wait: "bg-amber-bg text-amber",
  neutral: "bg-line-soft text-ink-3",
};

/// 结果徽章
export function Chip({ tone, text }: { tone?: Tone | undefined; text: string }) {
  return (
    <Reveal
      motionKey={`${tone}:${text}`}
      className={cx(
        "inline-flex h-[26px] items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 font-semibold font-ui text-[12.5px]",
        CHIP_CLASS[tone ?? "neutral"],
      )}
    >
      <Inline text={text} />
    </Reveal>
  );
}

export interface ResultRow {
  label: string;
  value: string | { text: string; tone: Tone }[];
  tone?: Tone | undefined;
}

/// 结果表：一行一个结果，value 是一串时显示「A 然后 B」
export function ResultRows({
  rows,
  format = (s) => s,
}: {
  rows: readonly ResultRow[];
  format?: (s: string) => string;
}) {
  return (
    <div className="flex flex-col gap-2.5">
      {rows.map((r) => (
        <div
          key={r.label}
          className="flex items-center justify-between gap-3 rounded-inner border border-line-soft bg-surface-soft px-3.5 py-3"
        >
          <span className="font-ui text-ink-2 text-sm">
            <Inline text={format(r.label)} />
          </span>
          <span className="flex flex-wrap items-center justify-end gap-2">
            {typeof r.value === "string" ? (
              <Chip tone={r.tone} text={format(r.value)} />
            ) : (
              r.value.map((c, i, all) => (
                <span
                  key={occurrenceKeys(all.map((x) => x.text))[i]}
                  className="inline-flex items-center gap-2"
                >
                  {i > 0 ? <span className="text-ink-4 text-meta">然后</span> : null}
                  <Chip tone={c.tone} text={format(c.text)} />
                </span>
              ))
            )}
          </span>
        </div>
      ))}
    </div>
  );
}

/// 小标题（「先自己想一想」「建议先读这篇前置」等）
export function Meta({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span className={cx("font-ui text-ink-4 text-meta leading-normal", className)}>{children}</span>
  );
}
