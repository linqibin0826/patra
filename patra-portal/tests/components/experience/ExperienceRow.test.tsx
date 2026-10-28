import { render, screen } from "@testing-library/react";
import { makeCard } from "@tests/fixtures/discussion";
import { makeExperience, makeUserExperience } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExperienceRow } from "@/components/experience/ExperienceRow";
import type { Experience } from "@/lib/interaction/experience-schema";

const NOW = Date.parse("2026-10-28T02:00:00Z");

function renderRow(overrides: Partial<Experience> = {}) {
  render(<ExperienceRow variant="journal" experience={makeExperience(overrides)} now={NOW} />);
  return screen.getByRole("link");
}

describe("期刊页的一行（简报 §3.14）", () => {
  it("五行：月份与类型、结果、细节、摘录、作者行；整行链到经验页", () => {
    const row = renderRow({ body: "**审稿人**有两位" });
    expect(row).toHaveAttribute("href", "/posts/9");
    expect(row).toHaveTextContent("2026 年 3 月投稿 · 原创研究");
    expect(row).toHaveTextContent("首轮 42 天 · 大修 → 录用");
    expect(row).toHaveTextContent("投稿到录用 91 天 · 审稿 2 轮");
    expect(row).toHaveTextContent("审稿人有两位");
    expect(row).not.toHaveTextContent("**");
    expect(row).toHaveTextContent("陈宇");
    expect(row).toHaveTextContent("发布于 6 月 3 日");
  });

  it("不显示回复数，作者名不是单独的链接", () => {
    const row = renderRow();
    expect(row.querySelectorAll("a")).toHaveLength(0);
    expect(row).not.toHaveTextContent("回复");
  });

  it("已编辑", () => {
    expect(renderRow({ edited: true })).toHaveTextContent("已编辑");
  });

  it("exp.item.anonymous：别人看是匿名作者", () => {
    const row = renderRow({ anonymous: true, author: null });
    expect(row).toHaveTextContent("匿名作者");
    expect(row).not.toHaveTextContent("陈宇");
  });

  it("exp.item.mine：你写的", () => {
    expect(renderRow({ mine: true })).toHaveTextContent("你写的");
  });

  it("本人的匿名经验：匿名胶囊", () => {
    const row = renderRow({ mine: true, anonymous: true });
    expect(row).toHaveTextContent("匿名作者（你）");
    expect(row).toHaveTextContent("匿名");
  });

  it("exp.item.user-deleted：已注销用户", () => {
    const row = renderRow({
      author: makeCard({ state: "DELETED", displayName: null, username: null }),
    });
    expect(row).toHaveTextContent("已注销用户");
  });

  it("exp.item.in-progress：→ 进行中", () => {
    expect(
      renderRow({ finalOutcome: "IN_PROGRESS", finalDecisionOn: null, totalDays: null }),
    ).toHaveTextContent("首轮 42 天 · 大修 → 进行中");
  });

  it("exp.item.desk-reject：没有箭头", () => {
    const row = renderRow({
      firstDecision: "DESK_REJECT",
      finalOutcome: "REJECTED",
      firstRoundDays: 9,
      totalDays: 9,
    });
    expect(row).toHaveTextContent("首轮 9 天 · 直接拒稿");
    expect(row).not.toHaveTextContent("→");
  });

  it("只有必填：没有细节行与摘录", () => {
    const row = renderRow({
      finalDecisionOn: null,
      totalDays: null,
      reviewRounds: null,
      reviewQuality: null,
      editorCommunication: null,
      apcPaid: null,
      previouslyRejected: null,
      body: null,
    });
    expect(row.querySelector("[data-detail]")).toBeNull();
    expect(row.querySelector("[data-excerpt]")).toBeNull();
  });
});

describe("主页标签的一行（简报 §3.17）", () => {
  it("第一行是期刊名，第二行合并月份、类型与结果", () => {
    render(<ExperienceRow variant="profile" item={makeUserExperience()} now={NOW} />);
    const row = screen.getByRole("link");
    expect(row).toHaveAttribute("href", "/posts/9");
    expect(row).toHaveTextContent("Journal of the American Heart Association");
    expect(row).toHaveTextContent("2026 年 3 月投稿 · 原创研究 · 首轮 42 天 · 大修 → 录用");
    expect(row).toHaveTextContent("发布于 6 月 3 日");
  });

  it("本人的匿名经验：匿名 · 仅你可见", () => {
    render(
      <ExperienceRow
        variant="profile"
        item={makeUserExperience({ anonymous: true, mine: true })}
        now={NOW}
      />,
    );
    expect(screen.getByRole("link")).toHaveTextContent("匿名 · 仅你可见");
  });
});
