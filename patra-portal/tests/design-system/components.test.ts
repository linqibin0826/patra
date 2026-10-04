import { describe, expect, it } from "vitest";
import { findAll, format, readSrc, sourceFiles } from "./source-scan";

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

  it("首页文献条目 hover：顶部陶土线自左生长 + 标题下划线绘出（编辑排版，无卡片框）", () => {
    const src = comp("portal/PaperCard.tsx");
    for (const cls of [
      "group/paper",
      "before:bg-clay-500",
      "hover:before:w-full",
      "group-hover/paper:[background-size:100%_1px]",
    ]) {
      expect(src).toContain(cls);
    }
  });

  it("分页格圆角为 radius-md", () => {
    expect(comp("portal/browse/BrowsePagination.tsx")).toMatch(
      /const CELL =\s*"[^"]*\brounded-md\b/,
    );
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

  it("所有 fieldset 带 min-w-0（默认 min-width: min-content 会撑破侧栏，期刊学科筛选溢出的根因）", () => {
    for (const f of sourceFiles()) {
      for (const m of f.text.matchAll(
        /<fieldset[\s\S]*?className=(?:"([^"]*)"|\{[^}]*?"([^"]*)")/g,
      )) {
        expect(m[1] ?? m[2], f.path).toMatch(/\bmin-w-0\b/);
      }
    }
  });

  it("12 栏网格只在 lg 以上启用（窄屏下 12 列 × 栏距的最小宽度会撑破容器，内容被裁）", () => {
    const hits = findAll(/(?<![\w:-])(?:[\w-]+:)*grid-cols-12\b/).filter(
      ({ match }) => !/(?:^|:)(?:lg|xl|2xl):grid-cols-12$/.test(match),
    );
    expect(format(hits)).toEqual([]);
  });

  it("btnBlock 经 cn() 与按钮类串合并（模板字符串拼接时 btnBlock 的 px-4 会被 BTN_BASE 的 px-5 盖掉）", () => {
    expect(format(findAll(/\$\{btn(?:Primary|Secondary)\}\s+\$\{btnBlock\}/))).toEqual([]);
  });

  it("本版不做暗色：sonner 不依赖 next-themes", () => {
    expect(comp("ui/sonner.tsx")).not.toMatch(/next-themes/);
  });
});
