import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeNotification } from "@tests/fixtures/notification";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NotificationRow } from "@/components/notifications/NotificationRow";

const NOW = Date.parse("2026-10-27T10:00:00Z");

describe("NotificationRow", () => {
  it("未读：圆点与浅底；三层文案；整行链到永久链接，时间悬停看完整时间", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    const row = screen.getByRole("link");
    expect(row).toHaveAttribute("href", "/papers/11?comment=9");
    expect(row).toHaveAttribute("data-unread", "true");
    expect(row).toHaveTextContent("陈宇回复了你的评论");
    expect(screen.getByText("2 小时前")).toHaveAttribute("title", "2026-10-27 16:00");
    expect(
      screen.getByText("同意。补充一点：文中的倾向评分只用来校正主要结局"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("《Hydrocortisone, Vitamin C, and Thiamine for Sepsis》的讨论"),
    ).toBeInTheDocument();
  });

  it("未读在读屏名称里说出来", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    expect(screen.getByRole("link", { name: /^未读，陈宇回复了你的评论/ })).toBeInTheDocument();
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

  it("匿名作者、评论已删除", () => {
    render(
      <NotificationRow
        notification={makeNotification({
          actor: null,
          actorAnonymous: true,
          commentDeleted: true,
          excerpt: null,
        })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByRole("link")).toHaveTextContent("匿名作者回复了你的评论");
    expect(screen.getByText("该评论已删除")).toBeInTheDocument();
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
    screen.getByRole("button", { name: /陈宇回复了你的评论/ }).focus();
    await userEvent.keyboard("{Enter}");
    expect(onOpen).toHaveBeenCalledWith(notification);
  });

  it("已读：名字、动作、摘录整体降一级", () => {
    render(
      <NotificationRow
        notification={makeNotification({ read: true })}
        now={NOW}
        onOpen={vi.fn()}
      />,
    );
    expect(screen.getByText("陈宇")).toHaveClass("text-fg-2");
    expect(screen.getByText("回复了你的评论")).toHaveClass("text-fg-3");
    expect(screen.getByText("同意。补充一点：文中的倾向评分只用来校正主要结局")).toHaveClass(
      "text-fg-3",
    );
  });

  it("未读：可见的人名字 fg-1、动作与摘录 fg-2", () => {
    render(<NotificationRow notification={makeNotification()} now={NOW} onOpen={vi.fn()} />);
    expect(screen.getByText("陈宇")).toHaveClass("text-fg-1");
    expect(screen.getByText("回复了你的评论")).toHaveClass("text-fg-2");
    expect(screen.getByText("同意。补充一点：文中的倾向评分只用来校正主要结局")).toHaveClass(
      "text-fg-2",
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

  it("行的内边距是上下 16px、左右 0（简报 §3.19）", () => {
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

  it("上下文行的文献标题按内联标记渲染：白名单标签按格式显示，不露出尖括号", () => {
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
      "《Fecal Microbiota and Bifidobacterium longum Treatment of CO2 & Mice》的讨论",
    );
    expect(row.querySelector("i")).toHaveTextContent("Bifidobacterium longum");
    expect(row.querySelector("sub")).toHaveTextContent("2");
  });
});
