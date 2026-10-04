import { describe, expect, it } from "vitest";
import {
  btnPrimary,
  btnSecondary,
  CHECK_INPUT,
  RADIO_INPUT,
  sourceDotClass,
} from "@/lib/portal-ui";

const tokens = (cls: string) => cls.split(/\s+/).filter(Boolean);

describe("按钮类串", () => {
  it.each([
    ["btnPrimary", btnPrimary],
    ["btnSecondary", btnSecondary],
  ])("%s 的 hover / 按下反馈不作用于禁用态", (_, cls) => {
    const stateful = tokens(cls).filter((t) => /(?:^|:)(?:hover|active):/.test(t));
    expect(stateful.length).toBeGreaterThan(0);
    for (const t of stateful) {
      expect(t, t).toMatch(/(?:^|:)not-disabled:/);
    }
  });

  it("hover 前移只作用于标了 data-icon=trailing 的末尾图标（前置图标不动）", () => {
    expect(btnPrimary).toMatch(/\[&>svg\[data-icon=trailing\]\]:translate-x-0\.5/);
    expect(btnPrimary).not.toMatch(/svg:last-child/);
  });
});

describe("勾选控件类串", () => {
  it.each([
    ["CHECK_INPUT", CHECK_INPUT],
    ["RADIO_INPUT", RADIO_INPUT],
  ])("%s 的 hover 只加深未选中控件的描边，选中态保持 action-primary", (_, cls) => {
    expect(tokens(cls)).toContain("not-checked:hover:border-ink-500");
    expect(tokens(cls)).not.toContain("hover:border-ink-500");
  });
});

describe("sourceDotClass", () => {
  it("已知来源给设计系统的来源色", () => {
    expect(sourceDotClass("PubMed")).toBe("bg-source-pubmed");
    expect(sourceDotClass("Europe PMC")).toBe("bg-source-epmc");
    expect(sourceDotClass("Crossref")).toBe("bg-source-crossref");
  });

  it("未知来源回退 source-other", () => {
    expect(sourceDotClass("OpenAlex")).toBe("bg-source-other");
  });
});
