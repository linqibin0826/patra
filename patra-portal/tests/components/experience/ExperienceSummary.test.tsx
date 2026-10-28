import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeFewSummary, makeSummary } from "@tests/fixtures/experience";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExperienceSummary } from "@/components/experience/ExperienceSummary";
import type { Summary } from "@/lib/interaction/experience-schema";

function renderSummary(props: Partial<Parameters<typeof ExperienceSummary>[0]> = {}) {
  const onWindowChange = vi.fn();
  const onRetry = vi.fn();
  const view = render(
    <ExperienceSummary
      state={{ kind: "ok", summary: makeSummary() }}
      window="3y"
      onWindowChange={onWindowChange}
      onRetry={onRetry}
      {...props}
    />,
  );
  return { ...view, onWindowChange, onRetry };
}

const card = () => screen.getByRole("article", { name: "同行经验" });

describe("exp.summary.full（简报 §3.13）", () => {
  it("主数字、P25–P75、直接拒稿、录用率、结果分布与口径", () => {
    renderSummary();
    const box = card();
    expect(box).toHaveTextContent("首轮用时 · 中位数");
    expect(box).toHaveTextContent("42天");
    expect(box).toHaveTextContent("约 6 周 · P25–P75 28–63 天");
    expect(within(box).getByText("9 天")).toBeInTheDocument();
    expect(within(box).getByText("50%")).toBeInTheDocument();
    expect(box).toHaveTextContent("已完结的 12 条：录用 6、拒稿 6");
    expect(box).toHaveTextContent("录用 6 · 拒稿 6 · 撤稿 1 · 进行中 2");
    expect(box).toHaveTextContent("15 条经验 · 近 3 年（按投稿日期）· 首轮用时不含直接拒稿");
    expect(box).toHaveTextContent("来源：Patra 用户填写");
  });

  it("首轮用时不满 28 天时不写约几周", () => {
    renderSummary({
      state: {
        kind: "ok",
        summary: makeSummary({ firstRound: { median: 20, p25: 14, p75: 25, sampleSize: 6 } }),
      },
    });
    expect(card()).toHaveTextContent("P25–P75 14–25 天");
    expect(card()).not.toHaveTextContent("约");
  });

  it("统计不出来的项写破折号", () => {
    renderSummary({
      state: {
        kind: "ok",
        summary: makeSummary({ deskReject: null, acceptance: null, firstRound: null }),
      },
    });
    const box = card();
    expect(within(box).getAllByText("—").length).toBeGreaterThanOrEqual(3);
    expect(box).not.toHaveTextContent("已完结的");
  });
});

describe("近 3 年 / 全部", () => {
  it("分段控件按下的是当前窗口，点另一个交给上层切换", async () => {
    const user = userEvent.setup();
    const { onWindowChange } = renderSummary();
    const group = screen.getByRole("group", { name: "统计范围" });
    expect(within(group).getByRole("button", { name: "近 3 年" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await user.click(within(group).getByRole("button", { name: "全部" }));
    expect(onWindowChange).toHaveBeenCalledWith("all");
  });

  it("全部窗口的口径行", () => {
    renderSummary({
      window: "all",
      state: { kind: "ok", summary: makeSummary({ window: "all", count: 20 }) },
    });
    expect(card()).toHaveTextContent("20 条经验 · 全部 · 首轮用时不含直接拒稿");
  });
});

describe("样本不足与空", () => {
  it("exp.summary.few：只显示条数", () => {
    renderSummary({ state: { kind: "ok", summary: makeFewSummary(3) } });
    expect(card()).toHaveTextContent("3条");
    expect(card()).toHaveTextContent("近 3 年有 3 条投稿经验，满 5 条后显示统计。");
    expect(card()).not.toHaveTextContent("中位数");
  });

  it("全部窗口下不足 5 条", () => {
    renderSummary({
      window: "all",
      state: { kind: "ok", summary: { ...makeFewSummary(4), window: "all" } },
    });
    expect(card()).toHaveTextContent("一共有 4 条投稿经验，满 5 条后显示统计。");
  });

  it("exp.summary.window-empty：看全部切到全部窗口", async () => {
    const user = userEvent.setup();
    const { onWindowChange } = renderSummary({
      state: { kind: "ok", summary: makeFewSummary(0, 12) },
    });
    expect(card()).toHaveTextContent("近 3 年还没有投稿经验。");
    await user.click(screen.getByRole("button", { name: "看全部 12 条" }));
    expect(onWindowChange).toHaveBeenCalledWith("all");
  });

  it("exp.summary.empty：没有切换控件", () => {
    renderSummary({ state: { kind: "ok", summary: makeFewSummary(0, 0) } });
    expect(card()).toHaveTextContent("还没有同行分享这本期刊的投稿经验。");
    expect(screen.queryByRole("group", { name: "统计范围" })).not.toBeInTheDocument();
  });
});

describe("加载与不可用", () => {
  it("exp.summary.loading：骨架", () => {
    renderSummary({ state: { kind: "loading" } });
    expect(card()).toHaveAttribute("aria-busy", "true");
  });

  it("exp.summary.unavailable：重试", async () => {
    const user = userEvent.setup();
    const { onRetry } = renderSummary({ state: { kind: "unavailable" } });
    expect(card()).toHaveTextContent("同行经验暂不可用");
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalled();
  });
});

describe("写后变化的数字高亮（spec 5.3）", () => {
  it("同一窗口的新数据里变了的项带标记", () => {
    const { rerender, onWindowChange, onRetry } = renderSummary({
      state: { kind: "ok", summary: makeFewSummary(4) },
    });
    const next: Summary = makeSummary({ count: 5 });
    rerender(
      <ExperienceSummary
        state={{ kind: "ok", summary: next }}
        window="3y"
        onWindowChange={onWindowChange}
        onRetry={onRetry}
      />,
    );
    expect(card().querySelectorAll("[data-changed]").length).toBeGreaterThan(0);
  });

  it("切窗口带来的变化不高亮", () => {
    const { rerender, onWindowChange, onRetry } = renderSummary();
    rerender(
      <ExperienceSummary
        state={{ kind: "ok", summary: makeSummary({ window: "all", count: 20 }) }}
        window="all"
        onWindowChange={onWindowChange}
        onRetry={onRetry}
      />,
    );
    expect(card().querySelectorAll("[data-changed]")).toHaveLength(0);
  });
});
