import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useRef, useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { MarkdownEditor } from "@/components/discussion/MarkdownEditor";
import { useMentionSuggest } from "@/components/discussion/MentionSuggest";
import { COMMENT_LIMITS } from "@/lib/discussion/limits";
import type { MentionSuggestion } from "@/lib/interaction/discussion-schema";

const SUBJECT = { type: "publication", key: "1" } as const;
const fetchMock = vi.fn<typeof fetch>();

const person = (
  username: string,
  displayName: string,
  group: MentionSuggestion["group"] = "PARTICIPANT",
): MentionSuggestion => ({
  userId: String(username.length),
  username,
  displayName,
  avatarUrl: null,
  avatarPalette: 1,
  group,
});

function Editor() {
  const [value, setValue] = useState("");
  const ref = useRef<HTMLTextAreaElement | null>(null);
  const mention = useMentionSuggest({
    subject: SUBJECT,
    value,
    onChange: setValue,
    textareaRef: ref,
  });
  return (
    <MarkdownEditor
      value={value}
      onChange={setValue}
      label="评论内容"
      placeholder=""
      limits={COMMENT_LIMITS}
      limitLabel="评论"
      mentions={{ subject: SUBJECT }}
      textareaRef={ref}
      {...mention.editorProps}
    />
  );
}

function renderEditor() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <Editor />
    </QueryClientProvider>,
  );
  return screen.getByRole("combobox", { name: "评论内容" });
}

/// 在文本框里打字：改值并把光标放到末尾
function type(box: HTMLElement, value: string) {
  fireEvent.change(box, { target: { value } });
  (box as HTMLTextAreaElement).setSelectionRange(value.length, value.length);
  fireEvent.select(box);
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

describe("@ 联想", () => {
  it("只输入 @：列出本讨论的参与者", async () => {
    fetchMock.mockResolvedValue(Response.json([person("zhou-min", "周敏")]));
    const box = renderEditor();
    type(box, "@");
    expect(await screen.findByText("本讨论的参与者")).toBeInTheDocument();
    expect(await screen.findByRole("option", { name: /周敏/ })).toBeInTheDocument();
    expect(box).toHaveAttribute("aria-expanded", "true");
    expect(String(fetchMock.mock.calls[0]?.[0])).toBe(
      "/api/discussions/publication/1/mention-suggestions?q=",
    );
  });

  it("还没有参与者", async () => {
    fetchMock.mockResolvedValue(Response.json([]));
    type(renderEditor(), "@");
    expect(await screen.findByText("输入用户名或昵称查找同行")).toBeInTheDocument();
  });

  it("两组：参与者与其他用户", async () => {
    fetchMock.mockResolvedValue(
      Response.json([person("zhou-min", "周敏"), person("zhang-li", "张立", "OTHER")]),
    );
    type(renderEditor(), "@zh");
    expect(await screen.findByText("其他用户")).toBeInTheDocument();
    expect(screen.getAllByRole("option")).toHaveLength(2);
  });

  it("没有结果", async () => {
    fetchMock.mockResolvedValue(Response.json([]));
    type(renderEditor(), "@chenyu");
    expect(
      await screen.findByText("没有找到「chenyu」。输入完整的用户名也能 @ 到对方。"),
    ).toBeInTheDocument();
  });

  it("接口失败", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    type(renderEditor(), "@zh");
    expect(
      await screen.findByText("暂时查不到用户，输入完整的用户名也能 @ 到对方。"),
    ).toBeInTheDocument();
  });

  it("↓ + Enter 选中：换成 @用户名 加空格，联想关闭", async () => {
    fetchMock.mockResolvedValue(
      Response.json([person("zhou-min", "周敏"), person("zhang-li", "张立", "OTHER")]),
    );
    const box = renderEditor();
    type(box, "看 @zh");
    await screen.findAllByRole("option");
    fireEvent.keyDown(box, { key: "ArrowDown" });
    expect(box.getAttribute("aria-activedescendant")).toBe(
      screen.getAllByRole("option")[1]?.getAttribute("id"),
    );
    fireEvent.keyDown(box, { key: "Enter" });
    expect(box).toHaveValue("看 @zhang-li ");
    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
  });

  it("输入法组字中的 Enter 不选中", async () => {
    fetchMock.mockResolvedValue(Response.json([person("zhou-min", "周敏")]));
    const box = renderEditor();
    type(box, "@zh");
    await screen.findByRole("option");
    fireEvent.keyDown(box, { key: "Enter", isComposing: true });
    expect(box).toHaveValue("@zh");
  });

  it("Safari 用 Enter 提交组字（isComposing 为假、keyCode 229）：不选中", async () => {
    fetchMock.mockResolvedValue(Response.json([person("zhou-min", "周敏")]));
    const box = renderEditor();
    type(box, "@zh");
    await screen.findByRole("option");
    fireEvent.keyDown(box, { key: "Enter", keyCode: 229 });
    expect(box).toHaveValue("@zh");
  });

  it("Esc 关闭", async () => {
    fetchMock.mockResolvedValue(Response.json([person("zhou-min", "周敏")]));
    const box = renderEditor();
    type(box, "@zh");
    await screen.findByRole("listbox");
    act(() => {
      fireEvent.keyDown(box, { key: "Escape" });
    });
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(box).toHaveAttribute("aria-expanded", "false");
  });

  it("没有结果且查询词超过 6 个字：自动收起", async () => {
    fetchMock.mockResolvedValue(Response.json([]));
    type(renderEditor(), "@abcdefg");
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByRole("listbox")).toBeNull());
    expect(screen.queryByText(/没有找到/)).toBeNull();
  });
});
