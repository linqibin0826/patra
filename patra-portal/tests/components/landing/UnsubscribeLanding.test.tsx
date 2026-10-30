import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { UnsubscribeLanding } from "@/components/landing/UnsubscribeLanding";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import type { UnsubscribeLink, UnsubscribeResult } from "@/lib/notification/schema";
import { readUnsubscribeLink, unsubscribe } from "@/lib/notification/unsubscribe-actions";

vi.mock("@/lib/notification/unsubscribe-actions", () => ({
  readUnsubscribeLink: vi.fn(),
  unsubscribe: vi.fn(),
}));
vi.mock("@/lib/auth/current-user-query", () => ({ useCurrentUser: vi.fn() }));

const TOKEN = "Ab3_-".repeat(8).concat("xyz");

const LINK: UnsubscribeLink = {
  maskedEmail: "c***@example.com",
  category: "MENTION",
  reply: "INSTANT",
  mention: "INSTANT",
  alreadyUnsubscribed: false,
  othersEnabled: true,
};

const TYPE_DONE: UnsubscribeResult = {
  category: "MENTION",
  turnedOff: ["MENTION"],
  reply: "INSTANT",
  mention: "OFF",
  othersEnabled: true,
};

const ALL_DONE: UnsubscribeResult = {
  category: "ALL",
  turnedOff: ["REPLY"],
  reply: "OFF",
  mention: "OFF",
  othersEnabled: false,
};

beforeEach(() => {
  vi.mocked(useCurrentUser).mockReturnValue({ status: "anonymous" });
  vi.mocked(readUnsubscribeLink).mockResolvedValue({ ok: true, link: LINK });
});

afterEach(() => {
  window.history.replaceState(null, "", "/");
  vi.clearAllMocks();
});

/// 地址栏打开带片段的退订确认页
function renderLanding(hash = `#token=${TOKEN}&category=MENTION`, strict = false) {
  window.history.replaceState(null, "", `/unsubscribe${hash}`);
  render(
    strict ? (
      <StrictMode>
        <UnsubscribeLanding />
      </StrictMode>
    ) : (
      <UnsubscribeLanding />
    ),
  );
  return userEvent.setup();
}

const heading = (name: string) => screen.findByRole("heading", { level: 1, name });

describe("UnsubscribeLanding · 打开", () => {
  it("先出骨架；读到链接后是就绪态：地址、邮件、主按钮有焦点、关闭全部；只读不写，片段已抹掉", async () => {
    renderLanding();
    expect(screen.getByRole("status")).toHaveTextContent("正在读取退订链接");
    expect(await heading("退订提醒邮件")).toBeInTheDocument();
    expect(screen.getByText("退订后不再给这个地址发这类邮件，站内提醒照常。")).toBeInTheDocument();
    expect(screen.getByText("c***@example.com")).toBeInTheDocument();
    expect(screen.getByText("「@ 我的」提醒 · 离站时逐条")).toBeInTheDocument();
    const main = screen.getByRole("button", { name: "退订这类邮件" });
    await waitFor(() => expect(main).toHaveFocus());
    expect(screen.getByRole("button", { name: "关闭全部提醒邮件" })).toBeInTheDocument();
    expect(
      screen.getByText("账号安全邮件（验证邮箱、找回密码等）不受影响，也不能退订。"),
    ).toBeInTheDocument();
    expect(readUnsubscribeLink).toHaveBeenCalledExactlyOnceWith(TOKEN, "MENTION");
    expect(unsubscribe).not.toHaveBeenCalled();
    expect(window.location.hash).toBe("");
  });

  it("严格模式下也只读一次", async () => {
    renderLanding(undefined, true);
    expect(await heading("退订提醒邮件")).toBeInTheDocument();
    expect(readUnsubscribeLink).toHaveBeenCalledTimes(1);
  });

  it("其余都已关闭：不出「关闭全部提醒邮件」", async () => {
    vi.mocked(readUnsubscribeLink).mockResolvedValue({
      ok: true,
      link: { ...LINK, othersEnabled: false },
    });
    renderLanding();
    expect(await heading("退订提醒邮件")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "关闭全部提醒邮件" })).not.toBeInTheDocument();
  });

  it("每日汇总邮件：主按钮「退订每日汇总」，邮件一行列出汇总里的类别", async () => {
    vi.mocked(readUnsubscribeLink).mockResolvedValue({
      ok: true,
      link: { ...LINK, category: "DIGEST", mention: "DIGEST" },
    });
    renderLanding(`#token=${TOKEN}&category=DIGEST`);
    expect(await screen.findByRole("button", { name: "退订每日汇总" })).toBeInTheDocument();
    expect(screen.getByText("每日汇总 · 包括「@ 我的」")).toBeInTheDocument();
  });

  it("这一类已经退订过：出已经退订过，不出主按钮", async () => {
    vi.mocked(readUnsubscribeLink).mockResolvedValue({
      ok: true,
      link: { ...LINK, mention: "OFF", alreadyUnsubscribed: true },
    });
    renderLanding();
    expect(await heading("「@ 我的」提醒邮件已经退订过了")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "退订这类邮件" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "同时关闭其余提醒邮件" })).toBeInTheDocument();
  });

  it.each([
    ["缺类别", `#token=${TOKEN}`],
    ["类别不认识", `#token=${TOKEN}&category=ALL`],
    ["没有片段", ""],
  ])("%s：直接无效屏，不请求后端", async (_, hash) => {
    renderLanding(hash);
    expect(await heading("退订链接无效")).toBeInTheDocument();
    expect(readUnsubscribeLink).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: "登录后去通知设置" })).toHaveAttribute(
      "href",
      "/login?next=%2Fsettings%2Fnotifications",
    );
  });

  it("后端说链接无效：无效屏", async () => {
    vi.mocked(readUnsubscribeLink).mockResolvedValue({ ok: false, reason: "invalid" });
    renderLanding();
    expect(await heading("退订链接无效")).toBeInTheDocument();
  });

  it("读失败：「暂时打不开」屏，点重试再读一次，成功进就绪态", async () => {
    vi.mocked(readUnsubscribeLink).mockResolvedValueOnce({ ok: false, reason: "unavailable" });
    const user = renderLanding();
    expect(await heading("暂时打不开退订链接")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await heading("退订提醒邮件")).toBeInTheDocument();
    expect(readUnsubscribeLink).toHaveBeenCalledTimes(2);
    expect(readUnsubscribeLink).toHaveBeenLastCalledWith(TOKEN, "MENTION");
  });
});

describe("UnsubscribeLanding · 提交", () => {
  it("点「退订这类邮件」：提交中两个按钮都不能点；成功出已退订这一类", async () => {
    let answer: (value: Awaited<ReturnType<typeof unsubscribe>>) => void = () => {};
    vi.mocked(unsubscribe).mockReturnValue(
      new Promise((resolve) => {
        answer = resolve;
      }),
    );
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "退订这类邮件" }));
    expect(unsubscribe).toHaveBeenCalledWith(TOKEN, "MENTION");
    expect(screen.getByRole("button", { name: "退订中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("button", { name: "关闭全部提醒邮件" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    await act(async () => answer({ ok: true, result: TYPE_DONE }));
    expect(await heading("已退订「@ 我的」提醒邮件")).toBeInTheDocument();
    expect(
      screen.getByText("以后有人在评论里 @ 你，不再给这个地址发邮件；提醒页里照常能看到。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "同时关闭其余提醒邮件" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "登录后去通知设置" })).toHaveAttribute(
      "href",
      "/login?next=%2Fsettings%2Fnotifications",
    );
    expect(screen.getByText("点错了？登录后在通知设置里改回来。")).toBeInTheDocument();
  });

  it("结果屏点「同时关闭其余提醒邮件」：提交 ALL，出已关闭全部", async () => {
    vi.mocked(unsubscribe)
      .mockResolvedValueOnce({ ok: true, result: TYPE_DONE })
      .mockResolvedValueOnce({ ok: true, result: ALL_DONE });
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "退订这类邮件" }));
    await user.click(await screen.findByRole("button", { name: "同时关闭其余提醒邮件" }));
    expect(unsubscribe).toHaveBeenLastCalledWith(TOKEN, "ALL");
    expect(await heading("已关闭全部提醒邮件")).toBeInTheDocument();
    expect(screen.getByText("想重新收提醒邮件，登录后到通知设置里打开。")).toBeInTheDocument();
  });

  it("就绪态点「关闭全部提醒邮件」：提交 ALL，出已关闭全部", async () => {
    vi.mocked(unsubscribe).mockResolvedValue({ ok: true, result: ALL_DONE });
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "关闭全部提醒邮件" }));
    expect(unsubscribe).toHaveBeenCalledWith(TOKEN, "ALL");
    expect(await heading("已关闭全部提醒邮件")).toBeInTheDocument();
  });

  it("别处已经关了（turnedOff 为空）：出已经退订过", async () => {
    vi.mocked(unsubscribe).mockResolvedValue({
      ok: true,
      result: { ...TYPE_DONE, turnedOff: [], othersEnabled: false },
    });
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "退订这类邮件" }));
    expect(await heading("「@ 我的」提醒邮件已经退订过了")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "同时关闭其余提醒邮件" })).not.toBeInTheDocument();
  });

  it("提交失败：回到就绪态，出红色提示条", async () => {
    vi.mocked(unsubscribe).mockResolvedValue({ ok: false, reason: "unavailable" });
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "退订这类邮件" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("暂时没能退订，请稍后再试。");
    expect(screen.getByRole("button", { name: "退订这类邮件" })).not.toHaveAttribute(
      "aria-disabled",
    );
  });

  it("提交时链接无效：无效屏", async () => {
    vi.mocked(unsubscribe).mockResolvedValue({ ok: false, reason: "invalid" });
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "退订这类邮件" }));
    expect(await heading("退订链接无效")).toBeInTheDocument();
  });

  it("已登录：「去通知设置」直接去设置页", async () => {
    vi.mocked(useCurrentUser).mockReturnValue({ status: "authenticated", user: USER });
    vi.mocked(unsubscribe).mockResolvedValue({ ok: true, result: ALL_DONE });
    const user = renderLanding();
    await user.click(await screen.findByRole("button", { name: "关闭全部提醒邮件" }));
    expect(await screen.findByRole("link", { name: "去通知设置" })).toHaveAttribute(
      "href",
      "/settings/notifications",
    );
  });
});
