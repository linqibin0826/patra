// patra-learn/src/teaching/minimal.tsx
// 教学组件的最小实现：只保证条目能渲染、参数与登记表一致；交互、动效与样式在下一步按画板替换
import type { ReactNode } from "react";
import type { PropsOf } from "./registry";

/// 条目页注入的本篇前置，作者不写
export interface Prerequisite {
  slug: string;
  title: string;
}

export type DiagnoseProps = PropsOf<"Diagnose"> & { prerequisites?: Prerequisite[] };

function Shell({ title, kind, children }: { title: string; kind: string; children?: ReactNode }) {
  return (
    <section className="rounded-xl border border-line bg-surface p-4" aria-label={title}>
      <p className="font-bold">
        {title} <span className="text-sm text-fog">{kind}</span>
      </p>
      {children}
    </section>
  );
}

function Options({ options }: { options: string[] }) {
  return (
    <ol>
      {options.map((o, i) => (
        <li key={o}>
          {"ABCD"[i]}. {o}
        </li>
      ))}
    </ol>
  );
}

export function Diagnose({ question, options, prerequisites = [] }: DiagnoseProps) {
  return (
    <Shell title={question} kind="开始前先答一题">
      <Options options={options} />
      {prerequisites.length > 0 ? (
        <p className="text-sm text-fog">先读：{prerequisites.map((p) => p.title).join("、")}</p>
      ) : null}
    </Shell>
  );
}

type SelfCheckProps = PropsOf<"Recap"> & { children?: ReactNode };

export function Recap({ prompt }: SelfCheckProps) {
  return <Shell title={prompt} kind="复述题" />;
}

export function Transfer({ prompt }: SelfCheckProps) {
  return <Shell title={prompt} kind="迁移题" />;
}

export function Quiz({ title = "考考自己", questions }: PropsOf<"Quiz">) {
  return (
    <Shell title={title} kind={`${questions.length} 题`}>
      {questions.map((q) => (
        <p key={q.question}>{q.question}</p>
      ))}
    </Shell>
  );
}

export function Structure({ title, nodes }: PropsOf<"Structure">) {
  return (
    <Shell title={title} kind="结构图">
      <p>{nodes.map((n) => n.label).join(" · ")}</p>
    </Shell>
  );
}

export function Sequence({ title, messages }: PropsOf<"Sequence">) {
  return (
    <Shell title={title} kind="时序图">
      <Options options={messages.map((m) => m.label)} />
    </Shell>
  );
}

export function StateMachine({ title, states }: PropsOf<"StateMachine">) {
  return (
    <Shell title={title} kind="状态机">
      <p>{states.map((s) => s.label).join(" → ")}</p>
    </Shell>
  );
}

export function Stepper({ title, steps }: PropsOf<"Stepper">) {
  return (
    <Shell title={title} kind="逐步动画">
      <p>{steps[0]?.caption}</p>
    </Shell>
  );
}

export function Params({ title, controls }: PropsOf<"Params">) {
  return (
    <Shell title={title} kind="参数与开关">
      <p>{controls.map((c) => c.label).join(" · ")}</p>
    </Shell>
  );
}

export function Compare({ title, variants }: PropsOf<"Compare">) {
  return (
    <Shell title={title} kind="对比切换">
      <p>{variants.map((v) => v.label).join(" / ")}</p>
    </Shell>
  );
}

const CALLOUT_LABELS = {
  lead: "一句话",
  analogy: "打个比方",
  insight: "顺带想通的",
  warn: "小心",
  cost: "代价",
} as const;

export function Callout({ kind, label, children }: PropsOf<"Callout"> & { children?: ReactNode }) {
  return (
    <Shell title={label ?? CALLOUT_LABELS[kind]} kind="">
      {children}
    </Shell>
  );
}

export function DebugTrail({ steps }: PropsOf<"DebugTrail">) {
  return (
    <Shell title="一路怎么查过来的" kind="排查路径">
      <Options options={steps.map((s) => s.hypothesis)} />
    </Shell>
  );
}

export function DecisionOptions({ options }: PropsOf<"DecisionOptions">) {
  return (
    <Shell title="选项" kind="决策">
      <Options options={options.map((o) => o.name)} />
    </Shell>
  );
}
