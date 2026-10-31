// patra-learn/src/teaching/callout.tsx
// 强调块：一句话 / 打个比方 / 顺带想通的 / 小心 / 代价（条目页画板的 l-card、l-panel-moss、l-callout）。纯排版，服务端渲染
import type { ReactNode } from "react";
import type { PropsOf } from "./registry";

const KINDS = {
  lead: {
    label: "一句话",
    box: "rounded-card border border-teal-pale bg-surface px-6 py-5.5 text-[15px] text-ink shadow-card [&>p]:font-display [&>p]:font-semibold [&>p]:text-[19px] [&>p]:leading-[1.75]",
    head: "text-teal",
  },
  analogy: {
    label: "打个比方",
    box: "rounded-card bg-moss px-5.5 py-4.5 text-ink",
    head: "text-decision",
  },
  insight: {
    label: "顺带想通的",
    box: "rounded-card bg-moss px-5.5 py-4.5 text-ink",
    head: "text-decision",
  },
  warn: {
    label: "小心",
    box: "rounded-card bg-wrong-bg px-4.5 py-4 text-ink-2",
    head: "text-wrong",
  },
  cost: {
    label: "代价",
    box: "rounded-card bg-amber-bg px-4.5 py-4 text-ink-2",
    head: "text-amber",
  },
} as const;

export function Callout({ kind, label, children }: PropsOf<"Callout"> & { children?: ReactNode }) {
  const k = KINDS[kind];
  return (
    <aside
      data-kind={kind}
      className={`flex flex-col gap-2 [&_p]:m-0 [&_ul]:m-0 [&_ul]:list-disc [&_ul]:pl-5 ${k.box}`}
    >
      <span className={`font-semibold font-ui text-meta tracking-[0.02em] ${k.head}`}>
        {label ?? k.label}
      </span>
      {children}
    </aside>
  );
}
