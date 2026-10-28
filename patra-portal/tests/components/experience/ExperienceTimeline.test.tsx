import { render, screen, within } from "@testing-library/react";
import { makeExperience } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExperienceFacts } from "@/components/experience/ExperienceFacts";
import { ExperienceTimeline } from "@/components/experience/ExperienceTimeline";
import type { Experience } from "@/lib/interaction/experience-schema";

function nodes(overrides: Partial<Experience> = {}) {
  render(<ExperienceTimeline experience={makeExperience(overrides)} />);
  const list = screen.getByRole("list", { name: "投稿时间线" });
  return within(list).getAllByRole("listitem");
}

describe("ExperienceTimeline（简报 §3.16）", () => {
  it("三个节点，用时写在文字里", () => {
    const [submit, first, final] = nodes();
    expect(submit).toHaveTextContent("投稿");
    expect(submit).toHaveTextContent("2026 年 3 月 2 日");
    expect(submit).toHaveTextContent("42 天");
    expect(first).toHaveTextContent("首轮：大修");
    expect(first).toHaveTextContent("2026 年 4 月 13 日");
    expect(first).toHaveTextContent("49 天");
    expect(final).toHaveTextContent("录用");
    expect(final).toHaveTextContent("2026 年 6 月 1 日");
  });

  it("进行中：最后一个节点没有日期，中间一段不标用时", () => {
    const list = nodes({ finalOutcome: "IN_PROGRESS", finalDecisionOn: null, totalDays: null });
    expect(list).toHaveLength(3);
    expect(list[2]).toHaveTextContent("进行中");
    expect(list[2]).not.toHaveTextContent("日");
    expect(list[1]).not.toHaveTextContent("天");
  });

  it("首轮即最终（直接拒稿）：只有两个节点", () => {
    const list = nodes({
      firstDecision: "DESK_REJECT",
      finalOutcome: "REJECTED",
      finalDecisionOn: null,
      firstRoundDays: 9,
      totalDays: 9,
    });
    expect(list).toHaveLength(2);
    expect(list[1]).toHaveTextContent("首轮：直接拒稿");
  });

  it("有最终结果没填日期：未填日期，中间一段不标用时", () => {
    const list = nodes({ finalDecisionOn: null, totalDays: null });
    expect(list[2]).toHaveTextContent("未填日期");
    expect(list[1]).not.toHaveTextContent("天");
  });
});

describe("ExperienceFacts", () => {
  it("只列填了的选填项，文章类型总在", () => {
    render(
      <ExperienceFacts
        experience={makeExperience({
          reviewQuality: null,
          apcPaid: null,
          previouslyRejected: true,
        })}
      />,
    );
    const terms = screen.getAllByRole("term").map((term) => term.textContent);
    expect(terms).toEqual(["文章类型", "审稿轮数", "编辑沟通", "转投"]);
    expect(screen.getByText("原创研究")).toBeInTheDocument();
    expect(screen.getByText("3 / 5")).toBeInTheDocument();
    expect(screen.getByText("是")).toBeInTheDocument();
  });
});
