import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeCard, makeComment, makeReply } from "@tests/fixtures/discussion";
import { USER } from "@tests/fixtures/user";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CommentComposer } from "@/components/discussion/CommentComposer";
import type { CurrentUser } from "@/lib/auth/types";
import { editComment, publishComment } from "@/lib/interaction/discussion-actions";

vi.mock("@/lib/interaction/discussion-actions", () => ({
  publishComment: vi.fn(),
  editComment: vi.fn(),
  deleteComment: vi.fn(),
}));
vi.mock("next/navigation", async (original) => ({
  ...(await original<typeof import("next/navigation")>()),
  usePathname: () => "/papers/1",
  useSearchParams: () => new URLSearchParams("comment=5"),
}));

const SUBJECT = { type: "publication", key: "1" } as const;
const SEED = {
  status: "authenticated",
  emailVerified: true,
  email: "a@b.c",
  avatar: { displayName: "陈宇", username: "chen-yu", avatarUrl: null, avatarPalette: 1 },
} as const;
const publish = vi.mocked(publishComment);
const edit = vi.mocked(editComment);

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json([])),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

function renderComposer(
  props: Partial<Parameters<typeof CommentComposer>[0]> = {},
  user: CurrentUser | null | undefined = undefined,
) {
  const onDone = vi.fn();
  const onCancel = vi.fn();
  const view = renderDiscussion(
    <CommentComposer
      subject={SUBJECT}
      mode={{ kind: "root" }}
      placeholder="写下你对这篇文献的看法、疑问或补充证据…"
      viewer={SEED}
      onDone={onDone}
      onCancel={onCancel}
      {...props}
    />,
    { user },
  );
  return { ...view, onDone, onCancel };
}

const box = () => screen.getByRole("combobox", { name: "评论内容" });

/// 按整段文字找元素（名字加粗，文字分在几个元素里）
function line(text: string) {
  return screen.getByText(
    (_, element) =>
      element?.textContent === text &&
      ![...(element?.children ?? [])].some((child) => child.textContent === text),
  );
}

describe("资格", () => {
  it("未登录：登录后参与讨论，带回当前地址", () => {
    renderComposer({}, null);
    expect(screen.getByRole("link", { name: "登录后参与讨论" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%2F1%3Fcomment%3D5",
    );
    expect(screen.getByRole("link", { name: "注册" })).toHaveAttribute(
      "href",
      "/register?next=%2Fpapers%2F1%3Fcomment%3D5",
    );
  });

  it("邮箱未验证：编辑器禁用，提示里带邮箱与重新发送", () => {
    renderComposer({}, { ...USER, email: "a@b.c", emailVerified: false });
    expect(box()).toBeDisabled();
    expect(screen.getByText("验证邮箱后才能发表评论。验证邮件已发到 a@b.c。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
  });
});

describe("发表", () => {
  it("空白时发表按钮禁用", () => {
    renderComposer();
    fireEvent.change(box(), { target: { value: "   " } });
    expect(screen.getByRole("button", { name: "发表" })).toBeDisabled();
  });

  it("收起态：点占位展开并聚焦", async () => {
    renderComposer({ collapsible: true });
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "写下你对这篇文献的看法、疑问或补充证据…" }));
    expect(box()).toHaveFocus();
  });

  it("提交中：发表中…、只读；成功后清空并回调", async () => {
    let resolve: (value: Awaited<ReturnType<typeof publishComment>>) => void = () => {};
    publish.mockReturnValue(new Promise((r) => (resolve = r)));
    const { onDone } = renderComposer();
    fireEvent.change(box(), { target: { value: "每组只有 47 例" } });
    await userEvent.setup().click(screen.getByRole("button", { name: "发表" }));
    expect(screen.getByRole("button", { name: "发表中…" })).toBeDisabled();
    expect(box()).toHaveAttribute("readonly");
    resolve({ ok: true, comment: makeComment() });
    await waitFor(() => expect(onDone).toHaveBeenCalledWith({ comment: makeComment() }));
    expect(box()).toHaveValue("");
    expect(publish).toHaveBeenCalledWith(SUBJECT, { body: "每组只有 47 例", parentId: null });
  });

  it("提交中再按 ⌘Enter、再点按钮：只发一次", async () => {
    publish.mockReturnValue(new Promise(() => {}));
    renderComposer();
    fireEvent.change(box(), { target: { value: "x" } });
    fireEvent.keyDown(box(), { key: "Enter", metaKey: true });
    fireEvent.keyDown(box(), { key: "Enter", metaKey: true });
    fireEvent.click(screen.getByRole("button", { name: "发表中…" }));
    expect(publish).toHaveBeenCalledTimes(1);
  });

  it("10 秒内第二条：提示条，内容保留", async () => {
    publish.mockResolvedValue({ ok: false, reason: "too-frequent", retryAfterSeconds: 8 });
    renderComposer();
    fireEvent.change(box(), { target: { value: "又一条" } });
    await userEvent.setup().click(screen.getByRole("button", { name: "发表" }));
    expect(await screen.findByText("发得太快了，请隔 10 秒再发。")).toBeInTheDocument();
    expect(box()).toHaveValue("又一条");
  });

  it("服务端说邮箱未验证：提示 + 重新发送", async () => {
    publish.mockResolvedValue({ ok: false, reason: "email-unverified" });
    renderComposer();
    fireEvent.change(box(), { target: { value: "x" } });
    await userEvent.setup().click(screen.getByRole("button", { name: "发表" }));
    expect(await screen.findByText("验证邮箱后才能发表评论。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
  });

  it("会话失效且有内容：先确认再去登录", async () => {
    publish.mockResolvedValue({ ok: false, reason: "signed-out" });
    renderComposer();
    fireEvent.change(box(), { target: { value: "x" } });
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "发表" }));
    expect(
      await screen.findByText("登录已过期，去重新登录？已写的内容会丢失。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%2F1%3Fcomment%3D5",
    );
    await user.click(screen.getByRole("button", { name: "留在这里" }));
    expect(screen.queryByText("登录已过期，去重新登录？已写的内容会丢失。")).toBeNull();
  });
});

describe("取消", () => {
  it("没内容：直接回调", async () => {
    const { onCancel } = renderComposer();
    await userEvent.setup().click(screen.getByRole("button", { name: "取消" }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("有内容：原地确认放弃", async () => {
    const { onCancel } = renderComposer();
    fireEvent.change(box(), { target: { value: "写了一半" } });
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(screen.getByText("放弃正在写的内容？")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "继续写" }));
    expect(onCancel).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "取消" }));
    await user.click(screen.getByRole("button", { name: "放弃" }));
    expect(onCancel).toHaveBeenCalled();
  });
});

describe("回复与编辑", () => {
  const root = makeComment();
  const target = makeReply("5", "1", {
    author: makeCard({ userId: "9", username: "zhou-min", displayName: "周敏" }),
  });

  it("回复楼里的回复：回复 @zhou-min + ×，点 × 改为回复这一楼", async () => {
    renderComposer({ mode: { kind: "reply", root, target } });
    expect(line("回复 @zhou-min")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "改为回复这一楼" }));
    expect(line("回复 陈宇 的评论")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "回复" })).toBeInTheDocument();
  });

  it("回复根评论：回复 陈宇 的评论，没有 ×", () => {
    renderComposer({ mode: { kind: "reply", root, target: root } });
    expect(line("回复 陈宇 的评论")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "改为回复这一楼" })).toBeNull();
  });

  it("回复目标已删除：提示，改为回复这一楼并按根评论重发", async () => {
    publish.mockResolvedValueOnce({ ok: false, reason: "reply-target-gone" });
    publish.mockResolvedValueOnce({ ok: true, comment: makeReply("6") });
    renderComposer({ mode: { kind: "reply", root, target } });
    fireEvent.change(box(), { target: { value: "追问" } });
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "回复" }));
    expect(await screen.findByText("要回复的评论已被删除，内容还在输入框里。")).toBeInTheDocument();
    expect(screen.getByText("回复这一楼")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "回复" }));
    expect(publish).toHaveBeenLastCalledWith(SUBJECT, { body: "追问", parentId: "1" });
  });

  it("编辑：按钮是保存；内容没变直接收起，不发请求", async () => {
    const { onCancel } = renderComposer({
      mode: { kind: "edit", comment: root, source: "每组只有 47 例" },
    });
    expect(box()).toHaveValue("每组只有 47 例");
    await userEvent.setup().click(screen.getByRole("button", { name: "保存" }));
    expect(edit).not.toHaveBeenCalled();
    expect(onCancel).toHaveBeenCalled();
  });
});
