// patra-learn/src/teaching/debug-trail.tsx
// 排查路径（排查条目画板「一路怎么查过来的」）：一步一个假设，读者先想怎么测，点「测一下，看结果」才展开；
// 排除的划掉，方向对的琥珀色，找到根因的水青色
"use client";

import { useState } from "react";
import type { PropsOf } from "./registry";
import { Button, cx, Inline, Meta, Reveal } from "./ui";

const DOT = {
  "ruled-out": "border-ink-5 bg-surface",
  closer: "border-amber bg-amber",
  found: "border-teal bg-teal",
} as const;

const QUESTION = {
  "ruled-out": "text-ink-4 line-through decoration-ink-5",
  closer: "text-amber",
  found: "text-teal",
} as const;

export function DebugTrail({ steps }: PropsOf<"DebugTrail">) {
  /// 已经展开结果的步数
  const [revealed, setRevealed] = useState(0);
  const all = revealed >= steps.length;
  const visible = steps.slice(0, Math.min(steps.length, revealed + 1));

  return (
    <section aria-label="排查路径" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <Meta>每一步先想想你会怎么测，再点开看结果</Meta>
        {!all ? (
          <Button kind="quiet" onClick={() => setRevealed(steps.length)}>
            全部展开
          </Button>
        ) : null}
      </div>
      <ol className="m-0 flex list-none flex-col p-0">
        {visible.map((step, i) => {
          const shown = i < revealed;
          const more = i < visible.length - 1;
          return (
            <li key={step.hypothesis} className="grid grid-cols-[22px_minmax(0,1fr)] gap-x-3.5">
              <Reveal className="flex flex-col items-center">
                <i
                  className={cx(
                    "mt-[7px] box-border block size-3 flex-none rounded-full border-2 transition-colors duration-280",
                    shown ? DOT[step.verdict] : "border-teal-mark bg-surface",
                  )}
                />
                {more ? <b className="mt-[3px] block min-h-2.5 w-0.5 flex-1 bg-line" /> : null}
              </Reveal>
              <div className="flex flex-col gap-1 pb-4.5">
                <span
                  data-verdict={shown ? step.verdict : "open"}
                  className={cx(
                    "font-bold font-display text-[16.5px] transition-colors duration-280",
                    shown ? QUESTION[step.verdict] : "text-ink",
                  )}
                >
                  <Inline text={step.hypothesis} />
                </span>
                {shown ? (
                  <Reveal className="text-[15px] text-ink-2">
                    <Inline text={step.result} />
                  </Reveal>
                ) : (
                  <Button className="mt-1 self-start" onClick={() => setRevealed(i + 1)}>
                    测一下，看结果
                  </Button>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
