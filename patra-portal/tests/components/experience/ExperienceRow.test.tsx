import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeCard } from "@tests/fixtures/discussion";
import { makeExperience, makeUserExperience } from "@tests/fixtures/experience";
import { renderDiscussion } from "@tests/support/discussion";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ExperienceRow } from "@/components/experience/ExperienceRow";
import type { Experience } from "@/lib/interaction/experience-schema";
import { setHelpful } from "@/lib/interaction/helpful-actions";

vi.mock("@/lib/interaction/helpful-actions", () => ({ setHelpful: vi.fn() }));

const NOW = Date.parse("2026-10-28T02:00:00Z");

/// 返回整行（`article`）；链接只是标题行
function renderRow(overrides: Partial<Experience> = {}) {
  renderDiscussion(
    <ExperienceRow variant="journal" experience={makeExperience(overrides)} now={NOW} />,
  );
  return screen.getByRole("article");
}

describe("期刊页的一行（简报 §3.14）", () => {
  it("五行：月份与类型、结果、细节、摘录、作者行；结果行是链到经验页的链接", () => {
    const row = renderRow({ body: "**审稿人**有两位" });
    const link = within(row).getByRole("link");
    expect(link).toHaveAttribute("href", "/posts/9");
    expect(link).toHaveAccessibleName("首轮 42 天 · 大修 → 录用");
    expect(row).toHaveTextContent("2026 年 3 月投稿 · 原创研究");
    expect(row).toHaveTextContent("首轮 42 天 · 大修 → 录用");
    expect(row).toHaveTextContent("投稿到录用 91 天 · 审稿 2 轮");
    expect(row).toHaveTextContent("审稿人有两位");
    expect(row).not.toHaveTextContent("**");
    expect(row).toHaveTextContent("陈宇");
    expect(row).toHaveTextContent("发布于 6 月 3 日");
  });

  it("没有回复时不显示回复数，只有一个链接（作者名不单独链接）", () => {
    const row = renderRow();
    expect(within(row).getAllByRole("link")).toHaveLength(1);
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
    renderDiscussion(<ExperienceRow variant="profile" item={makeUserExperience()} now={NOW} />);
    const row = screen.getByRole("article");
    expect(
      within(row).getByRole("link", { name: "Journal of the American Heart Association" }),
    ).toHaveAttribute("href", "/posts/9");
    expect(row).toHaveTextContent("Journal of the American Heart Association");
    expect(row).toHaveTextContent("2026 年 3 月投稿 · 原创研究 · 首轮 42 天 · 大修 → 录用");
    expect(row).toHaveTextContent("发布于 6 月 3 日");
  });

  it("本人的匿名经验：匿名 · 仅你可见", () => {
    renderDiscussion(
      <ExperienceRow
        variant="profile"
        item={makeUserExperience({ anonymous: true, mine: true })}
        now={NOW}
      />,
    );
    expect(screen.getByRole("article")).toHaveTextContent("匿名 · 仅你可见");
  });
});

describe("经验行的有帮助（简报 v0.15 §3.9）", () => {
  it("别人的经验：底部有按钮，不在链接里", () => {
    const row = renderRow({ helpfulCount: 2 });
    const button = within(row).getByRole("button", { name: "有帮助，2 人觉得有帮助" });
    expect(within(row).getByRole("link").contains(button)).toBe(false);
  });

  it("点按钮调用点有帮助", async () => {
    vi.mocked(setHelpful).mockResolvedValue({
      ok: true,
      result: { helpful: true, helpfulCount: 1 },
    });
    const row = renderRow();
    await userEvent.setup().click(within(row).getByRole("button", { name: "有帮助" }));
    expect(setHelpful).toHaveBeenCalledWith({ type: "post", id: "9" }, true);
  });

  it("Tab 先到链接再到按钮", async () => {
    renderRow({ helpfulCount: 2 });
    const user = userEvent.setup();
    await user.tab();
    expect(screen.getByRole("link")).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: /^有帮助/ })).toHaveFocus();
  });

  it("自己的经验只显示人数", () => {
    const row = renderRow({ mine: true, helpfulCount: 2 });
    expect(within(row).queryByRole("button", { name: /有帮助/ })).toBeNull();
    expect(row).toHaveTextContent("2 人觉得有帮助");
  });

  it("自己的经验为 0：没有底部行", () => {
    const row = renderRow({ mine: true, helpfulCount: 0 });
    expect(row).not.toHaveTextContent("有帮助");
    expect(row.querySelector("[data-row-foot]")).toBeNull();
  });

  it("这条已被删除：提示之后重取经验列表", async () => {
    vi.mocked(setHelpful).mockResolvedValue({ ok: false, reason: "gone" });
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { client } = renderDiscussion(
        <ExperienceRow variant="journal" experience={makeExperience()} now={NOW} />,
      );
      const invalidate = vi.spyOn(client, "invalidateQueries");
      await userEvent
        .setup({ advanceTimers: vi.advanceTimersByTime })
        .click(screen.getByRole("button", { name: "有帮助" }));
      expect(await screen.findByText("这条投稿经验已被删除")).toBeVisible();
      expect(invalidate).not.toHaveBeenCalled();
      await act(() => vi.advanceTimersByTimeAsync(4100));
      expect(invalidate).toHaveBeenCalledWith(
        expect.objectContaining({ queryKey: ["experience"] }),
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it("主页本人视角：只有人数", () => {
    renderDiscussion(
      <ExperienceRow
        variant="profile"
        item={makeUserExperience({ mine: true, helpfulCount: 3 })}
        now={NOW}
      />,
    );
    expect(screen.getByRole("article")).toHaveTextContent("3 人觉得有帮助");
  });
});

describe("经验行的回复数（简报 v0.15 §3.9）", () => {
  it("should_show_reply_count_in_foot_as_plain_text", () => {
    const row = renderRow({ replyCount: 3 });
    const foot = row.querySelector("[data-row-foot]");
    expect(foot).toHaveTextContent("3 条回复");
    expect(within(row).getAllByRole("link")).toHaveLength(1);
  });

  it("should_show_foot_with_only_reply_count_for_own_unhelped_experience", () => {
    const row = renderRow({ mine: true, helpfulCount: 0, replyCount: 2 });
    const foot = row.querySelector("[data-row-foot]");
    expect(foot).toHaveTextContent(/^2 条回复$/);
  });

  it("should_group_thousands", () => {
    expect(renderRow({ replyCount: 1500 })).toHaveTextContent("1,500 条回复");
  });

  it("should_show_reply_count_on_profile_row", () => {
    renderDiscussion(
      <ExperienceRow variant="profile" item={makeUserExperience({ replyCount: 4 })} now={NOW} />,
    );
    expect(screen.getByRole("article")).toHaveTextContent("4 条回复");
  });
});
