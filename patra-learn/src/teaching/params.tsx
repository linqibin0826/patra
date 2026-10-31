// patra-learn/src/teaching/params.tsx
// 参数滑块与开关（画板 C-Params）：控件在左，右侧实时给出结果表与一句结论；可先让读者预测再动手
"use client";

import { useId, useState } from "react";
import { FieldLabel, Switch } from "./controls";
import { type ControlValue, formatControlValue, interpolate, matchCase } from "./logic";
import type { PropsOf } from "./registry";
import {
  answeredStates,
  Button,
  Inline,
  letterIndex,
  OptionList,
  ResultRows,
  Reveal,
  Verdict,
  Widget,
} from "./ui";

export function Params({ title, guess, controls, cases }: PropsOf<"Params">) {
  const [values, setValues] = useState<Record<string, ControlValue>>(() =>
    Object.fromEntries(controls.map((c) => [c.id, c.default])),
  );
  const [picked, setPicked] = useState<number | null>(null);
  const sliderId = useId();
  const locked = Boolean(guess) && picked === null;
  const matched = matchCase(cases, values);
  const fill = (s: string) => interpolate(s, controls, values);
  const set = (id: string, v: ControlValue) => setValues((cur) => ({ ...cur, [id]: v }));

  return (
    <Widget label={title} title={title} kind="参数与开关">
      {guess ? (
        <div className="flex flex-col gap-2.5 rounded-card bg-teal-wash px-4.5 py-4">
          <span className="font-semibold font-ui text-meta text-teal">先猜一下，再动手试</span>
          <span className="font-display font-semibold text-[16.5px] leading-[1.65]">
            <Inline text={guess.question} />
          </span>
          <OptionList
            options={guess.options}
            states={
              picked === null
                ? []
                : answeredStates(guess.options.length, letterIndex(guess.answer), picked)
            }
            disabled={picked !== null}
            onPick={setPicked}
          />
          {picked === null ? (
            <div className="flex justify-end">
              <Button kind="quiet" onClick={() => setPicked(letterIndex(guess.answer))}>
                不猜，直接试
              </Button>
            </div>
          ) : (
            <Verdict
              tone={picked === letterIndex(guess.answer) ? "ok" : "bad"}
              head={picked === letterIndex(guess.answer) ? "猜对了。" : "不是这样。"}
            >
              <Inline text={guess.explain} />
            </Verdict>
          )}
        </div>
      ) : null}
      <div className="grid grid-cols-[minmax(0,5fr)_minmax(0,6fr)] gap-5.5 max-sm:grid-cols-1">
        <div className="flex flex-col gap-1.5">
          {controls.map((c) =>
            c.type === "switch" ? (
              <Switch
                key={c.id}
                label={c.label}
                checked={values[c.id] === true}
                disabled={locked}
                onChange={(v) => set(c.id, v)}
              />
            ) : (
              <label
                key={c.id}
                htmlFor={`${sliderId}-${c.id}`}
                className="mt-2.5 flex flex-col gap-1"
              >
                <span className="flex justify-between font-ui text-ink text-ui">
                  <span>
                    <Inline text={c.label} />
                  </span>
                  <span className="font-display font-semibold tabular-nums">
                    {formatControlValue(c, values[c.id] ?? c.default)}
                  </span>
                </span>
                <input
                  id={`${sliderId}-${c.id}`}
                  type="range"
                  min={c.min}
                  max={c.max}
                  step={c.step}
                  value={Number(values[c.id] ?? c.default)}
                  disabled={locked}
                  aria-label={c.label.replaceAll("`", "")}
                  onChange={(e) => set(c.id, Number(e.target.value))}
                  className="h-7 w-full cursor-pointer accent-teal disabled:cursor-not-allowed disabled:opacity-45"
                />
                <span className="flex justify-between font-ui text-ink-4 text-meta">
                  <span>{formatControlValue(c, c.min)}</span>
                  <span>{formatControlValue(c, c.max)}</span>
                </span>
              </label>
            ),
          )}
        </div>
        <div className="flex flex-col gap-3">
          {locked ? (
            <div className="flex min-h-24 items-center justify-center rounded-inner border border-line border-dashed px-4 text-center font-ui text-ink-4 text-ui">
              先猜一下，结果在这里出现
            </div>
          ) : matched ? (
            <>
              <FieldLabel>结果</FieldLabel>
              <ResultRows rows={matched.results ?? []} format={fill} />
              <Verdict tone={matched.verdict.tone} head={matched.verdict.head} motionKey={matched}>
                <Inline text={fill(matched.verdict.text)} />
              </Verdict>
            </>
          ) : (
            <Reveal className="rounded-inner border border-line border-dashed px-4 py-3 font-ui text-ink-4 text-ui">
              这个组合没有写结果。
            </Reveal>
          )}
        </div>
      </div>
    </Widget>
  );
}
