// patra-learn/src/teaching/decision-options.tsx
// 决策选项（决策条目画板「三个选项」）：点开看好处与代价，默认展开选中的那个，选中的标「选了这个」
"use client";

import { useState } from "react";
import type { PropsOf } from "./registry";
import { cx, Inline, LETTERS, Reveal } from "./ui";

export function DecisionOptions({ options }: PropsOf<"DecisionOptions">) {
  const [open, setOpen] = useState(() =>
    Math.max(
      0,
      options.findIndex((o) => o.chosen),
    ),
  );
  const cur = options[open];

  return (
    <section aria-label="决策选项" className="flex flex-col gap-3">
      <div
        className="grid gap-2.5 max-sm:grid-cols-1"
        style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
      >
        {options.map((o, i) => (
          <button
            key={o.name}
            type="button"
            aria-expanded={open === i}
            onClick={() => setOpen(i)}
            className={cx(
              "box-border flex cursor-pointer flex-col items-start gap-1.5 rounded-xl border-[1.5px] px-4 py-3.5 text-left font-body text-ink transition-[border-color,background-color] duration-160 ease-out hover:border-teal-mark",
              open === i ? "border-teal bg-teal-wash" : "border-line bg-surface",
            )}
          >
            <span className="flex items-center gap-2">
              <span
                className={cx(
                  "box-border inline-flex size-6 items-center justify-center rounded-full border font-semibold font-ui text-tiny",
                  o.chosen ? "border-decision bg-decision text-white" : "border-line text-ink-3",
                )}
              >
                {LETTERS[i]}
              </span>
              {o.chosen ? (
                <span className="font-semibold font-ui text-decision text-tiny">选了这个</span>
              ) : null}
            </span>
            <span className="font-bold font-display text-[16.5px]">
              <Inline text={o.name} />
            </span>
            <span className="text-ink-3 text-sm leading-[1.6]">
              <Inline text={o.summary} />
            </span>
          </button>
        ))}
      </div>
      {cur ? (
        <Reveal
          motionKey={open}
          className="grid grid-cols-2 gap-4 rounded-card border border-line bg-surface px-5 py-4 shadow-card max-sm:grid-cols-1"
        >
          <div className="flex flex-col gap-1">
            <span className="font-semibold font-ui text-meta text-correct">好处</span>
            <p className="m-0 text-[15px] text-ink-2 leading-[1.75]">
              <Inline text={cur.pros} />
            </p>
          </div>
          <div className="flex flex-col gap-1">
            <span className="font-semibold font-ui text-amber text-meta">代价</span>
            <p className="m-0 text-[15px] text-ink-2 leading-[1.75]">
              <Inline text={cur.cons} />
            </p>
          </div>
        </Reveal>
      ) : null}
    </section>
  );
}
