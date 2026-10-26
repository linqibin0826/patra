"use client";

import { cn } from "@/lib/utils";

export const CITATION_STYLES = [
  { id: "gb-t-7714-2015", name: "GB/T 7714-2015" },
  { id: "vancouver", name: "Vancouver" },
  { id: "apa-7", name: "APA" },
  { id: "ama-11", name: "AMA" },
] as const;
export type CitationStyle = (typeof CITATION_STYLES)[number]["id"];
export const CITATION_STYLE_IDS = CITATION_STYLES.map((style) => style.id);
/// 记住上次的引用格式（分享面板与复制引用对话框共用）
export const CITATION_STYLE_KEY = "patra.citation-style";

/// 引用格式分段控件（简报 §3.4）：四项单选，分享面板与复制引用对话框共用
export function CitationFormatTabs({
  value,
  onChange,
}: {
  value: CitationStyle;
  onChange: (style: CitationStyle) => void;
}) {
  return (
    <fieldset className="flex min-w-0 rounded-full border border-border-default bg-paper-100 p-0.5 max-[880px]:flex-wrap">
      <legend className="sr-only">引用格式</legend>
      {CITATION_STYLES.map((style) => (
        <label
          key={style.id}
          className={cn(
            "flex-1 cursor-pointer rounded-full px-3 py-1.5 text-center text-sm whitespace-nowrap transition-colors has-[:focus-visible]:shadow-(--ring-focus)",
            style.id === value
              ? "bg-paper-50 font-medium text-fg-1 shadow-sm"
              : "text-fg-2 hover:text-fg-1",
          )}
        >
          <input
            type="radio"
            name="citation-style"
            className="sr-only"
            checked={style.id === value}
            onChange={() => onChange(style.id)}
          />
          {style.name}
        </label>
      ))}
    </fieldset>
  );
}
