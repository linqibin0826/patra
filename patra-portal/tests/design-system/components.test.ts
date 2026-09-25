import { describe, expect, it } from "vitest";
import { findAll, format, readSrc } from "./source-scan";

const comp = (path: string) => readSrc(`components/${path}`);

describe("组件对齐设计系统", () => {
  it("AI 速读条：强调浅底 + 细描边 + radius-md，不用左侧色条", () => {
    const src = comp("portal/AISummaryBadge.tsx");
    expect(src).not.toMatch(/border-l-/);
    expect(src).toMatch(/border-accent-border/);
    expect(src).toMatch(/bg-accent-tint/);
    expect(src).toMatch(/rounded-md/);
  });

  it("信息卡片统一 radius-lg（指标卡、评级表、摘要空态）", () => {
    for (const path of [
      "portal/journal-detail/MetricBadge.tsx",
      "portal/journal-detail/RatingTable.tsx",
      "portal/paper-detail/AbstractBlock.tsx",
    ]) {
      expect(comp(path), path).not.toMatch(/rounded-md border/);
    }
  });

  it("可点击的文献卡 hover：上浮 1px + border-hover + shadow-md", () => {
    const src = comp("portal/PaperCard.tsx");
    for (const cls of ["hover:-translate-y-px", "hover:border-border-hover", "hover:shadow-md"]) {
      expect(src).toContain(cls);
    }
  });

  it("分页格圆角为 radius-md", () => {
    expect(comp("portal/browse/BrowsePagination.tsx")).toMatch(/const CELL = "[^"]*\brounded-md\b/);
  });

  it("摘要正文使用 leading-reading", () => {
    const src = comp("portal/paper-detail/AbstractBlock.tsx");
    expect(src).toMatch(/font-serif text-lg leading-reading/);
  });

  it("证据徽章文字保持实色（阶梯条未亮格的 30% 属图形，不在此列）", () => {
    expect(comp("portal/EvidenceBadge.tsx")).not.toMatch(/opacity-(?:[4-9]\d)\b/);
  });

  it("期刊封面装饰小字不透明度不低于 75%", () => {
    expect(comp("portal/JournalCoverCard.tsx")).not.toMatch(
      /tracking-spaced opacity-(?:[1-6]\d|70)\b/,
    );
  });

  it("eyebrow 统一复用 SectionEyebrow，不在各处手写", () => {
    const hits = findAll(/text-2xs font-semibold uppercase tracking-caps/).filter(
      (h) => !h.file.endsWith("SectionEyebrow.tsx"),
    );
    expect(format(hits)).toEqual([]);
  });

  it("本版不做暗色：sonner 不依赖 next-themes", () => {
    expect(comp("ui/sonner.tsx")).not.toMatch(/next-themes/);
  });
});
