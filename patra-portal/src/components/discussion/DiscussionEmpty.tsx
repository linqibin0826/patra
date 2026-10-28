import type { ReactNode } from "react";
import type { DiscussionCopy } from "@/lib/discussion/copy";

/// 讨论区为空（简报 §3.11）：衬线标题、两行说明、「可以讨论什么」三行提示，下面是展开的编辑器（或登录引导）
export function DiscussionEmpty({ copy, composer }: { copy: DiscussionCopy; composer: ReactNode }) {
  return (
    <div className="flex flex-col gap-3.5 pt-2 pb-1">
      <h3 className="font-serif text-xl font-medium text-fg-1">{copy.emptyTitle}</h3>
      <p className="max-w-[40em] text-md leading-relaxed whitespace-pre-line text-fg-2">
        {copy.emptyBody}
      </p>
      {copy.emptyGuide.length > 0 && (
        <ul className="flex flex-col gap-1.5 border-l-2 border-dashed border-border-default pl-3.5 text-sm leading-5 text-fg-3">
          {copy.emptyGuide.map((item) => (
            <li key={item.label}>
              <b className="mr-1.5 font-medium text-fg-2">{item.label}</b>
              {item.text}
            </li>
          ))}
        </ul>
      )}
      <div className="mt-2">{composer}</div>
    </div>
  );
}
