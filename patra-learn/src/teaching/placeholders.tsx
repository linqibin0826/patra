// patra-learn/src/teaching/placeholders.tsx
// 诊断题 / 复述题 / 迁移题的占位实现：只保证能渲染，交互与样式由 PAP-157 按画板替换。
// 名字、角色与作者参数以 registry.ts 为准
import Link from "next/link";
import type { ReactNode } from "react";

export interface DiagnoseProps {
  question: string;
  children?: ReactNode;
  /// 条目页注入的本篇前置，作者不写
  prerequisites?: { slug: string; title: string }[];
}

export function Diagnose({ question, children, prerequisites = [] }: DiagnoseProps) {
  return (
    <aside data-role="diagnostic" className="rounded-xl border border-line bg-surface p-4">
      <p className="font-bold">{question}</p>
      {children ? (
        <details>
          <summary>想好了再看</summary>
          {children}
        </details>
      ) : null}
      {prerequisites.length > 0 ? (
        <p className="text-sm text-fog">
          答不上来？先读：
          {prerequisites.map((p, i) => (
            <span key={p.slug}>
              {i > 0 ? "、" : null}
              <Link href={`/entries/${p.slug}`}>{p.title}</Link>
            </span>
          ))}
        </p>
      ) : null}
    </aside>
  );
}

interface QuestionProps {
  prompt: string;
  children?: ReactNode;
}

/// 复述题与迁移题共用的占位外观
function Question({ kind, prompt, children }: QuestionProps & { kind: string }) {
  return (
    <aside data-role={kind} className="rounded-xl border border-line bg-surface p-4">
      <p className="font-bold">{prompt}</p>
      {children ? (
        <details>
          <summary>参考答案</summary>
          {children}
        </details>
      ) : null}
    </aside>
  );
}

export function Recap(props: QuestionProps) {
  return <Question kind="recap" {...props} />;
}

export function Transfer(props: QuestionProps) {
  return <Question kind="transfer" {...props} />;
}
