// patra-learn/src/teaching/diagnose.tsx
// 开头诊断题（画板 C-Diagnose）：未答 / 答对 / 答错 / 答不上来 / 答错后直接往下读（收成一行）
"use client";

import Link from "next/link";
import { useState } from "react";
import type { PropsOf } from "./registry";
import {
  answeredStates,
  Button,
  buttonClass,
  cx,
  Inline,
  letterIndex,
  Meta,
  OptionList,
  Reveal,
  Verdict,
} from "./ui";

/// 条目页注入的本篇直接前置，作者不写；meta 是难度、时长、所在路线等一行说明
export interface Prerequisite {
  slug: string;
  title: string;
  meta?: string;
}

export type DiagnoseProps = PropsOf<"Diagnose"> & { prerequisites?: Prerequisite[] };

function QuestionIcon() {
  return (
    <svg
      aria-hidden="true"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 9.5a2.5 2.5 0 1 1 3.3 2.4c-.5.2-.8.6-.8 1.1v.5" />
      <path d="M12 17h.01" />
    </svg>
  );
}

export function Diagnose({
  question,
  options,
  answer,
  ifRight,
  ifWrong,
  suggest,
  prerequisites = [],
}: DiagnoseProps) {
  const [picked, setPicked] = useState<number | null>(null);
  const [dunno, setDunno] = useState(false);
  const [closed, setClosed] = useState(false);
  const right = letterIndex(answer);
  const done = picked !== null || dunno;
  const isRight = picked === right;
  const pre = prerequisites.find((p) => p.slug === suggest) ?? prerequisites[0];
  const reset = () => {
    setPicked(null);
    setDunno(false);
    setClosed(false);
  };

  if (closed) {
    return (
      <Reveal className="flex flex-wrap items-center gap-3 rounded-card border border-line bg-surface-soft py-2.5 pr-2.5 pl-4.5">
        <span
          className={cx("size-2 flex-none rounded-full", isRight ? "bg-teal" : "bg-teal-pale")}
        />
        <span className="flex-1 font-ui text-ink-3 text-ui">
          {isRight
            ? "开头这题答对了，已经有前置知识。"
            : pre
              ? `开头这题没答上，建议先读《${pre.title}》。`
              : "开头这题没答上，往下读时留意它考的那一点。"}
        </span>
        <Button kind="quiet" onClick={reset}>
          重新答
        </Button>
      </Reveal>
    );
  }

  return (
    <section
      aria-label="开头诊断题"
      className="overflow-hidden rounded-card border border-line bg-surface font-body text-ink shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 border-line-soft border-b px-4.5 py-3">
        <span className="inline-flex items-center gap-1.5 font-semibold font-ui text-ink-4 text-tiny tracking-[0.04em]">
          <QuestionIcon />
          开始前先答一题
        </span>
        <Meta>答不上也没关系，它只帮你判断要不要先读前置</Meta>
      </div>
      <div className="flex flex-col gap-4 px-5.5 py-5">
        <p className="m-0 font-display font-semibold text-[18px] leading-[1.65]">
          <Inline text={question} />
        </p>
        <OptionList
          options={options}
          states={done ? answeredStates(options.length, right, picked) : []}
          disabled={done}
          onPick={(i) => setPicked(i)}
        />
        {!done ? (
          <div className="flex justify-end">
            <Button kind="quiet" onClick={() => setDunno(true)}>
              我答不上来
            </Button>
          </div>
        ) : null}
        {done && isRight ? (
          <>
            <Verdict tone="ok" head="答对了。">
              <Inline text={ifRight} />
            </Verdict>
            <div className="flex justify-end">
              <Button onClick={() => setClosed(true)}>收起，开始读</Button>
            </div>
          </>
        ) : null}
        {done && !isRight ? (
          <>
            <Verdict tone="bad" head={dunno ? "没关系。" : "不太对。"}>
              <Inline text={ifWrong} />
            </Verdict>
            <Reveal className="flex flex-wrap items-center gap-4 rounded-card border border-line bg-surface-soft px-4.5 py-4">
              {pre ? (
                <div className="flex flex-[1_1_260px] flex-col gap-1">
                  <Meta>建议先读这篇前置</Meta>
                  <Link
                    href={`/entries/${pre.slug}`}
                    className="font-bold font-display text-[17px] text-ink hover:text-teal-deep"
                  >
                    {pre.title}
                  </Link>
                  {pre.meta ? <Meta>{pre.meta}</Meta> : null}
                </div>
              ) : (
                <span className="flex-[1_1_260px] font-ui text-ink-3 text-ui">
                  这一篇没有前置，往下读就是在讲它。
                </span>
              )}
              <div className="flex flex-wrap gap-2">
                {pre ? (
                  <Link href={`/entries/${pre.slug}`} className={buttonClass("primary")}>
                    先去读这篇
                  </Link>
                ) : null}
                <Button kind="quiet" onClick={() => setClosed(true)}>
                  我直接往下读
                </Button>
              </div>
            </Reveal>
          </>
        ) : null}
      </div>
    </section>
  );
}
