"use client";

import { useState } from "react";
import { Pill } from "@/components/portal/Pill";
import { RailRow } from "@/components/portal/RailRow";
import type { PaperDetail } from "@/types/portal";

/** 默认显示的项数，其余收在「另 N 项」后面。 */
const SHOWN = 8;

/// MeSH · 关键词（spec §5.5）：摘要后一行，主要主题词带 ★ 排在最前，再是其余主题词与关键词；
/// 默认 8 项，「另 N 项」点开后全部显示、不再收起。两类都为空时不渲染。
export function KeywordRow({ paper }: { paper: PaperDetail }) {
  const [expanded, setExpanded] = useState(false);
  const mesh = [...paper.meshHeadings].sort((a, b) => Number(b.major) - Number(a.major));
  const items = [
    ...mesh.map((h) => ({ key: `m-${h.descriptorUi}`, text: h.term, major: h.major })),
    ...paper.keywords.map((k) => ({ key: `k-${k}`, text: k, major: false })),
  ];
  if (items.length === 0) {
    return null;
  }
  const shown = expanded ? items : items.slice(0, SHOWN);
  const hidden = items.length - shown.length;

  return (
    <section aria-labelledby="keywords-title">
      <RailRow
        label={
          <h2
            id="keywords-title"
            className="m-0 pt-2 font-mono text-2xs font-medium tracking-caps text-fg-3"
          >
            MESH · 关键词
          </h2>
        }
      >
        <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
          {shown.map((item) => (
            <li key={item.key}>
              <Pill tone={item.major ? "clay" : "neutral"}>
                {item.major && <span title="主要主题词">★</span>}
                {item.text}
              </Pill>
            </li>
          ))}
        </ul>
        {hidden > 0 && (
          <button
            type="button"
            onClick={() => setExpanded(true)}
            className="mt-2 inline-flex items-center rounded-full border border-dashed border-border-strong px-3 py-1 font-sans text-sm text-fg-3 hover:text-fg-1"
          >
            另 {hidden} 项
          </button>
        )}
      </RailRow>
    </section>
  );
}
