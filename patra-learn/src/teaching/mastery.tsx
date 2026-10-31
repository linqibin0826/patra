// patra-learn/src/teaching/mastery.tsx
// 掌握度（画板 C-Mastery）：页面级组件，不进条目正文登记表。受控：值与上次标记时间由页面（PAP-162）从浏览器存储读出传入，
// 选中、取消都回调给页面；组件只管三档卡片、选中上浮、「记下了」提示 2.6 秒后消失
"use client";

import { useEffect, useState } from "react";
import { pop, useMotionOnChange } from "./motion";
import { Button, cx } from "./ui";

/// 0 没标过、1 刚接触、2 理解中、3 吃透了
export type MasteryLevel = 0 | 1 | 2 | 3;

export const MASTERY_LABELS = ["没读过", "刚接触", "理解中", "吃透了"] as const;

const LEVELS = [
  { level: 1, desc: "名字听过，细节还说不清" },
  { level: 2, desc: "能讲个大概，自己动手还要查" },
  { level: 3, desc: "能给别人讲清楚，也知道什么时候不该用" },
] as const;

/// 提示停留多久（毫秒）
const TOAST_MS = 2600;

/// 种子 → 芽 → 两片叶
function Sprout({ level }: { level: 1 | 2 | 3 }) {
  return (
    <svg
      aria-hidden="true"
      width="18"
      height="18"
      viewBox="0 0 26 26"
      fill="none"
      stroke="#0B6F7E"
      strokeWidth="2"
      strokeLinecap="round"
    >
      {level === 1 ? (
        <>
          <path d="M13 22v-3" />
          <circle cx="13" cy="15.5" r="2.5" fill="#0B6F7E" stroke="none" />
        </>
      ) : level === 2 ? (
        <>
          <path d="M13 22V13" />
          <path d="M13 15c-4 0-6-2.5-6-6 4 0 6 2.5 6 6z" />
        </>
      ) : (
        <>
          <path d="M13 23V11" />
          <path d="M13 14c-5 0-8-3-8-8 5 0 8 3 8 8z" />
          <path d="M13 11c0-4 3-7 8-7 0 4-3 7-8 7z" />
        </>
      )}
    </svg>
  );
}

export function Mastery({
  value,
  lastMarked,
  onChange,
}: {
  value: MasteryLevel;
  /// 上次标记的日期文字（如「10/26」），页面算好传入
  lastMarked?: string;
  onChange: (level: MasteryLevel) => void;
}) {
  const [saved, setSaved] = useState(false);
  const [initial] = useState(value);
  useEffect(() => {
    if (!saved) return;
    const t = setTimeout(() => setSaved(false), TOAST_MS);
    return () => clearTimeout(t);
  }, [saved]);
  const ref = useMotionOnChange<HTMLDivElement>(
    (el) => pop(el?.querySelector('[aria-pressed="true"]') ?? null),
    value,
  );

  return (
    <section
      aria-label="掌握度"
      className="overflow-hidden rounded-card border border-line bg-surface font-body text-ink shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-line-soft border-b px-4.5 py-3">
        <span className="font-bold font-display text-[16.5px]">读完了，现在觉得自己</span>
        <span className="font-ui text-ink-4 text-meta">
          {lastMarked && initial > 0
            ? `${lastMarked} 你标的是「${MASTERY_LABELS[initial]}」`
            : "只记在这个浏览器里，不用登录"}
        </span>
      </div>
      <div className="flex flex-col gap-3.5 px-5.5 py-5">
        <div ref={ref} className="grid grid-cols-3 gap-2.5 max-sm:grid-cols-1">
          {LEVELS.map(({ level, desc }) => (
            <button
              key={level}
              type="button"
              aria-pressed={value === level}
              onClick={() => {
                onChange(level);
                setSaved(true);
              }}
              className={cx(
                "box-border flex min-h-11 cursor-pointer flex-col items-start gap-1.5 rounded-xl border-[1.5px] p-4 text-left font-body text-ink transition-[border-color,background-color,transform] duration-280 ease-spring hover:border-teal-mark",
                value === level
                  ? "-translate-y-0.5 border-teal bg-teal-tint"
                  : "border-line bg-surface",
              )}
            >
              <span className="inline-flex items-center gap-2 font-semibold font-ui text-[15px]">
                <Sprout level={level} />
                {MASTERY_LABELS[level]}
              </span>
              <span className="text-ink-3 text-sm leading-[1.6]">{desc}</span>
            </button>
          ))}
        </div>
        <div className="flex min-h-9 flex-wrap items-center gap-3">
          {saved && value > 0 ? (
            <>
              <span className="inline-flex h-9 items-center gap-2 rounded-full bg-ink px-3.5 font-ui text-[13.5px] text-white">
                记下了：{MASTERY_LABELS[value]}
              </span>
              <span className="font-ui text-ink-4 text-meta">
                {value === 3 ? "首页以后很少再挑它，除非很久没看" : "过几天首页会挑它让你回顾"}
              </span>
            </>
          ) : null}
          {value > 0 && !saved ? (
            <>
              <span className="flex-1" />
              <Button kind="quiet" onClick={() => onChange(0)}>
                取消标记
              </Button>
            </>
          ) : null}
        </div>
      </div>
    </section>
  );
}
