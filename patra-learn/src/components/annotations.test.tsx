// @vitest-environment jsdom
// 条目页批注：按画布「学习站批注」Main 板的流程走一遍（选中 → 工具条 → 写 → 保存 → 徽章 → 查看 / 编辑 / 删除）
import { beforeEach, describe, expect, it } from "vitest";
import { ANNOTATIONS_KEY, type Annotation } from "@/lib/annotations/annotations";
import { act, fireEvent, render, screen, within } from "@/teaching/testing/render";
import { AnnotatableBody, EntryAnnotations, EntryNotesCard } from "./annotations";

const SLUG = "spring-modulith-event-publication-registry";

function Page() {
  return (
    <EntryAnnotations slug={SLUG}>
      <p>页面标题不在正文里</p>
      <AnnotatableBody>
        <p>事件发布登记就是为这个准备的。发布事件时，为每个监听器记一行。</p>
        <p>处理成功才算完成，失败的留着可以补投。</p>
      </AnnotatableBody>
      <EntryNotesCard />
    </EntryAnnotations>
  );
}

/// 在页面上选中一段文字，再松开鼠标
function select(text: string) {
  const node = [...document.querySelectorAll("p")]
    .map((p) => p.firstChild as Text)
    .find((t) => t.data.includes(text)) as Text;
  const start = node.data.indexOf(text);
  const range = document.createRange();
  range.setStart(node, start);
  range.setEnd(node, start + text.length);
  const selection = window.getSelection() as Selection;
  selection.removeAllRanges();
  selection.addRange(range);
  fireEvent.pointerUp(document);
}

function stored(): Annotation[] {
  return JSON.parse(window.localStorage.getItem(ANNOTATIONS_KEY) ?? "[]");
}

function seed(list: Partial<Annotation>[]) {
  window.localStorage.setItem(
    ANNOTATIONS_KEY,
    JSON.stringify(
      list.map((a, i) => ({
        id: `a${i + 1}`,
        slug: SLUG,
        kind: "question",
        text: "",
        createdAt: Date.UTC(2026, 10, 1),
        ...a,
      })),
    ),
  );
}

beforeEach(() => {
  window.localStorage.clear();
  window.getSelection()?.removeAllRanges();
});

describe("选中文字", () => {
  it("选中正文里的文字后出现工具条：有问题 / 记笔记", () => {
    render(<Page />);
    select("为每个监听器记一行");
    const bar = screen.getByRole("toolbar", { name: "批注选中的文字" });
    expect(within(bar).getByRole("button", { name: "有问题" })).toBeTruthy();
    expect(within(bar).getByRole("button", { name: "记笔记" })).toBeTruthy();
  });

  it("选的不是正文时不出现工具条", () => {
    render(<Page />);
    select("页面标题");
    expect(screen.queryByRole("toolbar")).toBeNull();
  });

  it("取消选中后工具条消失", () => {
    render(<Page />);
    select("为每个监听器记一行");
    window.getSelection()?.removeAllRanges();
    fireEvent.pointerUp(document);
    expect(screen.queryByRole("toolbar")).toBeNull();
  });
});

describe("写批注", () => {
  it("点「有问题」写下说明并保存：存进浏览器，句尾出现徽章，侧栏列出这一条", () => {
    render(<Page />);
    select("为每个监听器记一行");
    fireEvent.click(screen.getByRole("button", { name: "有问题" }));
    const dialog = screen.getByRole("dialog", { name: "写批注" });
    expect(within(dialog).getByText("“为每个监听器记一行”")).toBeTruthy();
    fireEvent.change(within(dialog).getByLabelText("哪里不对，或者哪里没看懂？"), {
      target: { value: "10 个监听器就是 10 行吗？" },
    });
    fireEvent.click(within(dialog).getByRole("button", { name: "保存" }));

    expect(stored()).toEqual([
      expect.objectContaining({
        slug: SLUG,
        kind: "question",
        text: "10 个监听器就是 10 行吗？",
        quote: expect.objectContaining({
          exact: "为每个监听器记一行",
          suffix: expect.stringMatching(/^。处理成功/),
        }),
      }),
    ]);
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("button", { name: "查看批注：有问题" })).toBeTruthy();
    const card = screen.getByRole("region", { name: "这一篇的批注" });
    expect(within(card).getByText("10 个监听器就是 10 行吗？")).toBeTruthy();
    expect(screen.getByRole("status").textContent).toContain("已记下");
  });

  it("写的时候可以切换成笔记", () => {
    render(<Page />);
    select("失败的留着");
    fireEvent.click(screen.getByRole("button", { name: "有问题" }));
    fireEvent.click(screen.getByRole("button", { name: "笔记" }));
    expect(screen.getByLabelText("用你自己的话记下来")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    expect(stored()[0]).toMatchObject({ kind: "note", text: "" });
  });

  it("取消或按 Esc 不保存", () => {
    render(<Page />);
    select("失败的留着");
    fireEvent.click(screen.getByRole("button", { name: "记笔记" }));
    fireEvent.keyDown(screen.getByRole("dialog", { name: "写批注" }), { key: "Escape" });
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(stored()).toEqual([]);
  });
});

describe("已有的批注", () => {
  it("打开页面时按原文挂回去，点徽章能看到说明", () => {
    seed([
      {
        text: "10 行吗？",
        quote: { exact: "为每个监听器记一行", prefix: "发布事件时，", suffix: "。" },
      },
    ]);
    render(<Page />);
    fireEvent.click(screen.getByRole("button", { name: "查看批注：有问题" }));
    const dialog = screen.getByRole("dialog", { name: "查看批注" });
    expect(within(dialog).getByText("10 行吗？")).toBeTruthy();
    expect(within(dialog).getByText("还没导出给 Claude")).toBeTruthy();
  });

  it("编辑能改说明和类型", () => {
    seed([{ text: "旧的", quote: { exact: "失败的留着", prefix: "", suffix: "" } }]);
    render(<Page />);
    fireEvent.click(screen.getByRole("button", { name: "查看批注：有问题" }));
    fireEvent.click(screen.getByRole("button", { name: "编辑" }));
    fireEvent.click(screen.getByRole("button", { name: "笔记" }));
    fireEvent.change(screen.getByLabelText("用你自己的话记下来"), { target: { value: "新的" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    expect(stored()[0]).toMatchObject({ kind: "note", text: "新的" });
    expect(screen.getByRole("button", { name: "查看批注：笔记" })).toBeTruthy();
  });

  it("删除后徽章和存储里都没了", () => {
    seed([{ quote: { exact: "失败的留着", prefix: "", suffix: "" } }]);
    render(<Page />);
    fireEvent.click(screen.getByRole("button", { name: "查看批注：有问题" }));
    fireEvent.click(screen.getByRole("button", { name: "删除" }));
    expect(stored()).toEqual([]);
    expect(screen.queryByRole("button", { name: /查看批注/ })).toBeNull();
  });

  it("侧栏里点一条，打开它的批注", () => {
    seed([
      { kind: "note", text: "我的笔记", quote: { exact: "失败的留着", prefix: "", suffix: "" } },
    ]);
    render(<Page />);
    const card = screen.getByRole("region", { name: "这一篇的批注" });
    fireEvent.click(within(card).getByRole("button", { name: /我的笔记/ }));
    expect(
      within(screen.getByRole("dialog", { name: "查看批注" })).getByText("我的笔记"),
    ).toBeTruthy();
  });

  it("原文找不到的标成原文已变：不画徽章，侧栏注明", async () => {
    seed([{ text: "改包名算吗？", quote: { exact: "已经删掉的一句话", prefix: "", suffix: "" } }]);
    render(<Page />);
    await act(async () => {
      await new Promise((r) => setTimeout(r, 0));
    });
    expect(stored()[0]?.orphaned).toBe(true);
    expect(screen.queryByRole("button", { name: /查看批注/ })).toBeNull();
    const card = screen.getByRole("region", { name: "这一篇的批注" });
    expect(within(card).getByText("原文已变")).toBeTruthy();
  });

  it("没有批注时侧栏提示怎么记", () => {
    render(<Page />);
    const card = screen.getByRole("region", { name: "这一篇的批注" });
    expect(within(card).getByText(/选中正文里的一段文字/)).toBeTruthy();
  });
});
