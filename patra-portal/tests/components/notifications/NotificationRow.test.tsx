import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeNotification } from "@tests/fixtures/notification";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NotificationRow } from "@/components/notifications/NotificationRow";

const NOW = Date.parse("2026-10-27T10:00:00Z");

describe("NotificationRow", () => {
  it("未读：有圆点、没有浅底；三层文案；整行链到永久链接，时间悬停看完整时间", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    const row = screen.getByRole("link");
    expect(row).toHaveAttribute("href", "/papers/11?comment=9");
    expect(row).toHaveAttribute("data-unread", "true");
    expect(row).not.toHaveClass("bg-paper-50");
    expect(row).toHaveTextContent("陈宇回复了你");
    expect(screen.getByText("2 小时前")).toHaveAttribute("title", "2026-10-27 16:00");
    expect(
      screen.getByText("同意。补充一点：文中的倾向评分只用来校正主要结局"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Hydrocortisone, Vitamin C, and Thiamine for Sepsis"),
    ).toBeInTheDocument();
    expect(row).not.toHaveTextContent("的讨论");
  });

  it("未读在读屏名称里说出来", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    expect(screen.getByRole("link", { name: /^未读，陈宇回复了你/ })).toBeInTheDocument();
  });

  it.each([
    ["昨天写时分", "2026-10-26T13:40:00Z", "21:40"],
    ["更早写月日", "2026-10-21T02:00:00Z", "10 月 21 日"],
  ])("时间按分组写：%s", (_, occurredAt, text) => {
    render(
      <NotificationRow
        notification={makeNotification({ occurredAt })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it("文献下线（没有摘录）：只有上下两行", () => {
    render(
      <NotificationRow
        notification={makeNotification({ excerpt: null })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByRole("link")).toHaveTextContent("陈宇回复了你");
    expect(screen.queryByText(/倾向评分/)).not.toBeInTheDocument();
    expect(
      screen.getByText("Hydrocortisone, Vitamin C, and Thiamine for Sepsis"),
    ).toBeInTheDocument();
  });

  it("已读：没有未读标记", () => {
    render(
      <NotificationRow
        notification={makeNotification({ read: true })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByRole("link")).toHaveAttribute("data-unread", "false");
  });

  it("匿名作者", () => {
    render(
      <NotificationRow
        notification={makeNotification({ actor: null, actorAnonymous: true })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByRole("link")).toHaveTextContent("匿名作者回复了你");
  });

  it("点一行先交给 onOpen（标已读），照常跳转", async () => {
    const onOpen = vi.fn();
    const notification = makeNotification();
    render(<NotificationRow notification={notification} now={NOW} onOpen={onOpen} />);
    await userEvent.click(screen.getByRole("link"));
    expect(onOpen).toHaveBeenCalledWith(notification);
  });

  it("对象 key 为空：不是链接，是按钮，键盘也能标已读", async () => {
    const onOpen = vi.fn();
    const notification = makeNotification({
      subject: {
        ...(makeNotification().subject as NonNullable<
          ReturnType<typeof makeNotification>["subject"]
        >),
        key: null,
      },
    });
    render(<NotificationRow notification={notification} now={NOW} onOpen={onOpen} />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    screen.getByRole("button", { name: /陈宇回复了你/ }).focus();
    await userEvent.keyboard("{Enter}");
    expect(onOpen).toHaveBeenCalledWith(notification);
  });

  it("已读：名字 fg-2 中粗、正文 fg-2，动作始终 fg-3", () => {
    render(
      <NotificationRow
        notification={makeNotification({ read: true })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByText("陈宇")).toHaveClass("text-fg-2", "font-medium");
    expect(screen.getByText("回复了你")).toHaveClass("text-fg-3");
    expect(screen.getByText("同意。补充一点：文中的倾向评分只用来校正主要结局")).toHaveClass(
      "text-fg-2",
    );
  });

  it("未读：名字 fg-1 加粗、正文 fg-1", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    expect(screen.getByText("陈宇")).toHaveClass("text-fg-1", "font-semibold");
    expect(screen.getByText("回复了你")).toHaveClass("text-fg-3");
    expect(screen.getByText("同意。补充一点：文中的倾向评分只用来校正主要结局")).toHaveClass(
      "text-fg-1",
    );
  });

  it.each([
    [false, "匿名作者", { actor: null, actorAnonymous: true }, "text-fg-2"],
    [false, "已注销用户", { actor: null }, "text-fg-3"],
    [true, "匿名作者", { actor: null, actorAnonymous: true }, "text-fg-3"],
    [true, "已注销用户", { actor: null }, "text-fg-3"],
  ] as const)("已读=%s：%s 的名字颜色按简报 §3.20", (read, name, overrides, color) => {
    render(
      <NotificationRow
        notification={makeNotification({ ...overrides, read })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByText(name)).toHaveClass(color);
  });

  it("行的内边距是上下 16px、左右 0（spec 4.3）", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    const row = screen.getByRole("link");
    expect(row).toHaveClass("py-4");
    expect(row.className).not.toMatch(/\bpx-/);
  });

  it("中键打开（新标签页）也标已读；右键（菜单）不算", () => {
    const onOpen = vi.fn();
    const notification = makeNotification();
    render(<NotificationRow notification={notification} now={NOW} onOpen={onOpen} />);
    const row = screen.getByRole("link");
    fireEvent(row, new MouseEvent("auxclick", { bubbles: true, button: 2 }));
    expect(onOpen).not.toHaveBeenCalled();
    fireEvent(row, new MouseEvent("auxclick", { bubbles: true, button: 1 }));
    expect(onOpen).toHaveBeenCalledWith(notification);
  });

  it("指针移入、聚焦（会触发预取）不算打开", async () => {
    const onOpen = vi.fn();
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={onOpen} />);
    await userEvent.hover(screen.getByRole("link"));
    screen.getByRole("link").focus();
    expect(onOpen).not.toHaveBeenCalled();
  });

  it("下行的文献标题按内联标记渲染：白名单标签按格式显示，不露出尖括号，不包书名号", () => {
    const base = makeNotification();
    const title =
      "Fecal Microbiota and <i>Bifidobacterium longum</i> Treatment of CO<sub>2</sub> &amp; Mice";
    render(
      <NotificationRow
        notification={makeNotification({
          subject: { ...(base.subject as NonNullable<typeof base.subject>), title },
        })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    const row = screen.getByRole("link");
    expect(row).not.toHaveTextContent("<i>");
    expect(row).toHaveTextContent(
      "Fecal Microbiota and Bifidobacterium longum Treatment of CO2 & Mice",
    );
    expect(row).not.toHaveTextContent("《");
    expect(row.querySelector("i")).toHaveTextContent("Bifidobacterium longum");
    expect(row.querySelector("sub")).toHaveTextContent("2");
  });

  describe("有帮助", () => {
    /// 周敏等 2 人点了你的投稿经验：最近一位是陈宇（默认触发人）
    const helpful = (overrides: Parameters<typeof makeNotification>[0] = {}) =>
      makeNotification({
        type: "POST_HELPFUL",
        actorCount: 2,
        commentId: null,
        excerpt: "第二轮小修后编辑直接接收",
        subject: {
          type: "post",
          key: "5",
          available: true,
          title: null,
          journal: null,
          abbreviation: "J Am Heart Assoc",
          submittedMonth: "2026-03",
          mine: true,
          experience: {
            firstDecision: "MAJOR_REVISION",
            finalOutcome: "ACCEPTED",
            firstRoundDays: 41,
            totalDays: 96,
          },
        },
        ...overrides,
      });

    it("合并条目：名字是最近一位，「等 N 人」跟在后面；去经验页；读屏先说未读", () => {
      render(<NotificationRow notification={helpful()} now={NOW} onOpen={vi.fn()} />);
      const row = screen.getByRole("link", { name: /^未读，陈宇等 2 人觉得你的投稿经验有帮助/ });
      expect(row).toHaveAttribute("href", "/posts/5");
      expect(
        screen.getByText("你的投稿经验 · J Am Heart Assoc · 2026 年 3 月投稿"),
      ).toBeInTheDocument();
    });

    it("2 人起头像压 +N−1 角标，读屏跳过；1 人没有角标", () => {
      const { unmount } = render(
        <NotificationRow notification={helpful({ actorCount: 3 })} now={NOW} onOpen={vi.fn()} />,
      );
      expect(screen.getByText("+2")).toHaveAttribute("aria-hidden", "true");
      unmount();
      render(
        <NotificationRow notification={helpful({ actorCount: 1 })} now={NOW} onOpen={vi.fn()} />,
      );
      expect(screen.queryByText(/^\+\d/)).not.toBeInTheDocument();
      expect(screen.getByRole("link")).toHaveTextContent("陈宇觉得你的投稿经验有帮助");
    });

    it("正文是你自己那条内容，未读也不加深", () => {
      render(<NotificationRow notification={helpful()} now={NOW} onOpen={vi.fn()} />);
      expect(screen.getByText("第二轮小修后编辑直接接收")).toHaveClass("text-fg-2");
    });

    it("经验没有正文：写结果行", () => {
      render(
        <NotificationRow notification={helpful({ excerpt: null })} now={NOW} onOpen={vi.fn()} />,
      );
      expect(screen.getByText("首轮 41 天 · 大修 → 录用")).toBeInTheDocument();
    });

    it("时间用 occurredAt", () => {
      render(
        <NotificationRow
          notification={helpful({ occurredAt: "2026-10-27T09:00:00Z" })}
          now={NOW}
          onOpen={vi.fn()}
        />,
      );
      expect(screen.getByRole("link").querySelector("time")).toHaveAttribute(
        "dateTime",
        "2026-10-27T09:00:00Z",
      );
    });
  });
});
