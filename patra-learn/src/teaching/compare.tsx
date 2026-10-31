// patra-learn/src/teaching/compare.tsx
// 对比切换（画板 C-Compare）：分段切换写法；开关模拟「崩在第 N 步之后」，之后的步骤划掉，结果换成崩溃时的
"use client";

import { useState } from "react";
import { FieldLabel, Segmented, Switch } from "./controls";
import { shake, useMotionOnChange } from "./motion";
import type { PropsOf } from "./registry";
import { cx, Inline, ResultRows, Verdict, Widget } from "./ui";

/// 崩溃分隔线：出现时抖一下
function CrashLine() {
  const ref = useMotionOnChange<HTMLDivElement>(shake, "crash");
  return (
    <div
      ref={ref}
      className="flex items-center gap-2 font-semibold font-ui text-[12.5px] text-wrong before:flex-1 before:border-wrong before:border-t-[1.5px] before:border-dashed after:flex-1 after:border-wrong after:border-t-[1.5px] after:border-dashed"
    >
      进程崩在这里
    </div>
  );
}

export function Compare({ title, failureLabel, variants }: PropsOf<"Compare">) {
  const [tab, setTab] = useState(0);
  const [crash, setCrash] = useState(false);
  const v = variants[tab] ?? variants[0];
  if (!v) return null;
  const failure = crash ? v.failure : undefined;
  const outcome = failure ?? v;

  return (
    <Widget label={title} title={title} kind="对比切换" bodyClassName="flex flex-col gap-4.5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Segmented
          label="写法"
          items={variants.map((x) => x.label)}
          value={tab}
          onChange={setTab}
        />
        {failureLabel ? <Switch label={failureLabel} checked={crash} onChange={setCrash} /> : null}
      </div>
      <div className="grid grid-cols-2 items-start gap-5 max-sm:grid-cols-1">
        <div className="flex flex-col gap-2.5">
          <FieldLabel>执行顺序</FieldLabel>
          {v.steps.map((step, i) => {
            const lost = failure !== undefined && i >= failure.after;
            return (
              <div key={step} className="flex flex-col gap-2.5">
                {failure !== undefined && i === failure.after ? <CrashLine /> : null}
                <div
                  data-state={lost ? "lost" : "run"}
                  className={cx(
                    "flex items-start gap-3 text-[15px] leading-[1.6] transition-colors duration-280",
                    lost ? "text-ink-5 line-through decoration-ink-5" : "text-ink-2",
                  )}
                >
                  <span className="inline-flex size-6 flex-none items-center justify-center rounded-full bg-line-soft font-semibold font-ui text-ink-3 text-tiny no-underline">
                    {i + 1}
                  </span>
                  <span>
                    <Inline text={step} />
                  </span>
                </div>
              </div>
            );
          })}
        </div>
        <div className="flex flex-col gap-2.5">
          <FieldLabel>结果</FieldLabel>
          <ResultRows rows={outcome.results ?? []} />
        </div>
      </div>
      <Verdict
        tone={outcome.verdict.tone}
        head={outcome.verdict.head}
        motionKey={`${tab}:${crash}`}
      >
        <Inline text={outcome.verdict.text} />
      </Verdict>
    </Widget>
  );
}
