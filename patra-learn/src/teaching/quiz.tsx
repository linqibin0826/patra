// patra-learn/src/teaching/quiz.tsx
// 考考自己（画板 C-Quiz）：未作答 / 选了还没对答案 / 答对 / 答错（可再试一次）/ 全部做完
"use client";

import { useState } from "react";
import type { PropsOf } from "./registry";
import {
  Button,
  Inline,
  letterIndex,
  OptionList,
  type OptionState,
  Reveal,
  StepDots,
  Verdict,
  Widget,
} from "./ui";

interface QuizState {
  index: number;
  picked: number | null;
  checked: boolean;
  /// 每题第一次对答案时对不对
  first: Record<number, boolean>;
  done: boolean;
}

const START: QuizState = { index: 0, picked: null, checked: false, first: {}, done: false };

export function Quiz({ title = "考考自己", questions }: PropsOf<"Quiz">) {
  const [s, setS] = useState<QuizState>(START);
  const total = questions.length;
  const q = questions[s.index] ?? questions[0];
  if (!q) return null;
  const right = letterIndex(q.answer);
  const isRight = s.picked === right;
  const states: OptionState[] = q.options.map((_, i) => {
    if (!s.checked) return s.picked === i ? "picked" : "idle";
    if (i === right) return isRight ? "right" : "dim";
    return i === s.picked ? "wrong" : "dim";
  });
  const score = Object.values(s.first).filter(Boolean).length;
  const last = s.index === total - 1;

  if (s.done) {
    return (
      <Widget label={title} title={title} kind={`共 ${total} 题`} bodyClassName="">
        <Reveal className="flex flex-wrap items-center gap-5">
          <div className="flex items-baseline gap-1.5">
            <span
              data-testid="quiz-score"
              className="font-display font-semibold text-[44px] text-teal tabular-nums leading-none"
            >
              {score}
            </span>
            <span className="font-ui text-ink-3 text-ui">/ {total} 题第一次就答对</span>
          </div>
          <div className="flex-[1_1_260px] font-ui text-ink-3 text-ui">
            {score === total
              ? "都答对了。可以去结尾那道题，换个场景再用一次。"
              : "答错的题，回到正文对应的小节再看一眼，做完结尾那道题再回来试试。"}
          </div>
          <Button onClick={() => setS(START)}>从头再做</Button>
        </Reveal>
      </Widget>
    );
  }

  return (
    <Widget
      label={title}
      title={title}
      kind={`第 ${s.index + 1} 题 / ${total}`}
      foot={
        <>
          <StepDots total={total} current={s.index} />
          <span className="flex gap-2">
            {s.checked && !isRight ? (
              <Button onClick={() => setS({ ...s, picked: null, checked: false })}>再试一次</Button>
            ) : null}
            {s.checked ? (
              <Button
                kind="primary"
                onClick={() =>
                  setS(
                    last
                      ? { ...s, done: true }
                      : { ...s, index: s.index + 1, picked: null, checked: false },
                  )
                }
              >
                {last ? "看结果" : "下一题"}
              </Button>
            ) : (
              <Button
                kind="primary"
                disabled={s.picked === null}
                onClick={() =>
                  setS({
                    ...s,
                    checked: true,
                    first: s.index in s.first ? s.first : { ...s.first, [s.index]: isRight },
                  })
                }
              >
                对一下答案
              </Button>
            )}
          </span>
        </>
      }
    >
      <p className="m-0 font-display font-semibold text-[17.5px] leading-[1.65]">
        <Inline text={q.question} />
      </p>
      <OptionList
        options={q.options}
        states={states}
        disabled={s.checked}
        onPick={(i) => setS({ ...s, picked: i })}
      />
      {s.checked ? (
        <Verdict
          tone={isRight ? "ok" : "bad"}
          head={isRight ? "对。" : "不对。"}
          motionKey={`${s.index}:${s.picked}`}
        >
          <Inline text={q.explain} />
        </Verdict>
      ) : null}
    </Widget>
  );
}
