// patra-learn/src/teaching/self-check.tsx
// 结尾迁移题 / 复述题（画板 C-Transfer）：还没写 / 写了一半 / 对照参考 / 自评完。写的东西不保存、不提交
"use client";

import { type ReactNode, useId, useState } from "react";
import type { PropsOf } from "./registry";
import { Button, cx, Inline, Meta, Reveal, Widget } from "./ui";

type SelfCheckProps = PropsOf<"Recap"> & { children?: ReactNode };

function CheckIcon() {
  return (
    <svg
      aria-hidden="true"
      width="12"
      height="12"
      viewBox="0 0 24 24"
      fill="none"
      stroke="#FFFFFF"
      strokeWidth="3.2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}

function SelfCheck({
  variant,
  prompt,
  points,
  placeholder,
  children,
}: SelfCheckProps & { variant: "recap" | "transfer" }) {
  const [text, setText] = useState("");
  const [shown, setShown] = useState(false);
  const [checked, setChecked] = useState<boolean[]>([]);
  const inputId = useId();
  const recall = variant === "recap";
  const got = points.filter((_, i) => checked[i]).length;
  const firstMissed = points.findIndex((_, i) => !checked[i]);
  const missed = points[firstMissed];
  const feedback =
    got === points.length
      ? recall
        ? "都说到了，你已经能把它讲给别人听。"
        : "都想到了，这个思路你已经能搬到别的系统里用。"
      : got === 0
        ? "还没打勾。对照上面几条，看看自己写到了哪些。"
        : `没打勾的「${missed?.label ?? `第 ${firstMissed + 1} 条`}」，回到正文对应的小节再看一眼。`;

  return (
    <Widget
      label={recall ? "复述题" : "迁移题"}
      title={recall ? "用自己的话复述" : "换个场景：迁移题"}
      kind={recall ? "结尾 · 复述题" : "结尾 · 迁移题"}
    >
      <p className="m-0 font-display font-semibold text-[17.5px] leading-[1.7]">
        <Inline text={prompt} />
      </p>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={inputId}>
          <Meta>先写下你的想法，几句话就够，不会被保存或提交</Meta>
        </label>
        <textarea
          id={inputId}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={placeholder ?? (recall ? "比如：它解决的是……" : "比如：我会先……")}
          className="box-border min-h-[120px] w-full resize-y rounded-inner border border-line bg-surface-soft px-3.5 py-3 font-body text-[15px] text-ink leading-[1.75] outline-none transition-[border-color,background-color] duration-160 ease-out focus:border-teal-mark focus:bg-surface"
        />
      </div>
      {!shown ? (
        <div className="flex justify-end gap-2">
          <Button kind="quiet" onClick={() => setShown(true)}>
            想不出来，直接看
          </Button>
          <Button kind="primary" disabled={text.trim() === ""} onClick={() => setShown(true)}>
            写好了，对照参考
          </Button>
        </div>
      ) : (
        <Reveal className="flex flex-col gap-2.5 pt-1.5">
          <span className="font-semibold font-ui text-ink-3 text-meta tracking-[0.02em]">
            参考要点 · 你写到了哪几条？点一下打勾
          </span>
          {points.map((p, i) => {
            const on = Boolean(checked[i]);
            return (
              <button
                key={p.text}
                type="button"
                aria-pressed={on}
                onClick={() =>
                  setChecked((c) => points.map((_, j) => (j === i ? !c[j] : Boolean(c[j]))))
                }
                className={cx(
                  "box-border flex w-full cursor-pointer items-start gap-3 rounded-inner border px-3.5 py-3 text-left font-body text-[15px] text-ink leading-[1.65] transition-[border-color,background-color] duration-160 ease-out",
                  on
                    ? "border-correct bg-correct-bg"
                    : "border-line bg-surface hover:border-teal-mark",
                )}
              >
                <span
                  className={cx(
                    "mt-[3px] box-border inline-flex size-5 flex-none items-center justify-center rounded-[5px] border-[1.5px] transition-colors duration-160",
                    on ? "border-correct bg-correct" : "border-ink-5",
                  )}
                >
                  {on ? <CheckIcon /> : null}
                </span>
                <span>
                  {p.label ? <b className="font-semibold font-ui">{p.label}：</b> : null}
                  <Inline text={p.text} />
                </span>
              </button>
            );
          })}
          <div className="flex items-start gap-2.5 rounded-inner bg-moss px-3.5 py-3 text-[15px] text-ink-2 leading-[1.7]">
            <span
              data-testid="self-check-score"
              className="flex-none font-display font-semibold text-decision tabular-nums"
            >
              {got}/{points.length}
            </span>
            <span>{feedback}</span>
          </div>
          {children ? (
            <div className="flex flex-col gap-2 rounded-inner border border-line-soft px-4 py-3 text-[15px] leading-[1.75]">
              <Meta>参考答案</Meta>
              {children}
            </div>
          ) : null}
        </Reveal>
      )}
    </Widget>
  );
}

export function Recap(props: SelfCheckProps) {
  return <SelfCheck variant="recap" {...props} />;
}

export function Transfer(props: SelfCheckProps) {
  return <SelfCheck variant="transfer" {...props} />;
}
