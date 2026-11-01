// @vitest-environment jsdom
// 「我的批注」页：按画布「学习站批注」Notes 板走一遍（分组、筛选、原文已变、导出给 Claude、删除、空状态）
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ANNOTATIONS_KEY, type Annotation } from "@/lib/annotations/annotations";
import { act, fireEvent, render, screen, within } from "@/teaching/testing/render";
import { NotesView } from "./notes-view";

const ENTRIES = {
  "spring-modulith-event-publication-registry": { title: "事件发布登记", type: "concept" as const },
  "msg-transactional-outbox": { title: "发件箱", type: "concept" as const },
};

const quote = (exact: string) => ({ exact, prefix: "", suffix: "" });

const SEED: Annotation[] = [
  {
    id: "q1",
    slug: "spring-modulith-event-publication-registry",
    kind: "question",
    text: "10 个监听器就是 10 行吗？",
    quote: quote("为每个监听器记一行"),
    createdAt: new Date(2026, 10, 1).getTime(),
  },
  {
    id: "n1",
    slug: "spring-modulith-event-publication-registry",
    kind: "note",
    text: "表里有行 = 有监听器没处理完",
    quote: quote("处理完的行直接删掉"),
    createdAt: new Date(2026, 9, 31).getTime(),
  },
  {
    id: "q2",
    slug: "msg-transactional-outbox",
    kind: "question",
    text: "改包名也算改类名吗？",
    quote: quote("登记行里存着事件的类名"),
    createdAt: new Date(2026, 9, 30).getTime(),
    exportedAt: new Date(2026, 9, 31).getTime(),
    orphaned: true,
  },
  {
    id: "q3",
    slug: "msg-transactional-outbox",
    kind: "question",
    text: "「不重」为什么做不到？",
    quote: quote("不保证不重"),
    createdAt: new Date(2026, 9, 31).getTime(),
  },
];

function stored(): Annotation[] {
  return JSON.parse(window.localStorage.getItem(ANNOTATIONS_KEY) ?? "[]");
}

const writeText = vi.fn<(text: string) => Promise<void>>();

beforeEach(() => {
  window.localStorage.clear();
  window.localStorage.setItem(ANNOTATIONS_KEY, JSON.stringify(SEED));
  writeText.mockReset().mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
});

describe("列表", () => {
  it("按条目分组，每条带原文与说明，筛选胶囊带数量", () => {
    render(<NotesView entries={ENTRIES} />);
    const group = screen.getByRole("region", { name: "事件发布登记" });
    expect(within(group).getByText("“为每个监听器记一行”")).toBeTruthy();
    expect(within(group).getByText("10 个监听器就是 10 行吗？")).toBeTruthy();
    expect(screen.getByRole("button", { name: "全部 4" }).getAttribute("aria-pressed")).toBe(
      "true",
    );
    expect(screen.getByRole("button", { name: "有问题 3" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "笔记 1" })).toBeTruthy();
  });

  it("筛选「有问题」时不显示笔记", () => {
    render(<NotesView entries={ENTRIES} />);
    fireEvent.click(screen.getByRole("button", { name: "有问题 3" }));
    expect(screen.queryByText("表里有行 = 有监听器没处理完")).toBeNull();
    expect(screen.getByText("10 个监听器就是 10 行吗？")).toBeTruthy();
  });

  it("原文已变的注明并说明怎么处理，导出过的注明日期", () => {
    render(<NotesView entries={ENTRIES} />);
    const item = screen.getByRole("article", { name: /改包名/ });
    expect(within(item).getByText("原文已变")).toBeTruthy();
    expect(within(item).getByText("10/31 导出过")).toBeTruthy();
    expect(within(item).getByText(/这段原文后来改过/)).toBeTruthy();
    expect(
      within(item).getByRole("link", { name: "看看原文现在怎么写" }).getAttribute("href"),
    ).toBe("/entries/msg-transactional-outbox");
  });

  it("删除一条", () => {
    render(<NotesView entries={ENTRIES} />);
    const item = screen.getByRole("article", { name: /不重/ });
    fireEvent.click(within(item).getByRole("button", { name: "删除" }));
    expect(stored().map((a) => a.id)).toEqual(["q1", "n1", "q2"]);
  });

  it("没有批注时说明怎么记", () => {
    window.localStorage.clear();
    render(<NotesView entries={ENTRIES} />);
    expect(screen.getByText("还没有批注")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /导出给 Claude/ })).toBeNull();
  });
});

describe("导出给 Claude", () => {
  it("预览没导出过的「有问题」，复制后标成导出过", async () => {
    render(<NotesView entries={ENTRIES} />);
    fireEvent.click(screen.getByRole("button", { name: "导出给 Claude · 2 条" }));
    const dialog = screen.getByRole("dialog", { name: "导出给 Claude" });
    const preview = within(dialog).getByTestId("export-preview").textContent ?? "";
    expect(preview).toContain("# 学习站批注：有问题 2 条");
    expect(preview).toContain("## 1. 事件发布登记");
    expect(preview).toContain(
      `页面：${window.location.origin}/entries/spring-modulith-event-publication-registry`,
    );
    expect(preview).toContain("## 2. 发件箱");
    expect(preview).not.toContain("改包名");

    await act(async () => {
      fireEvent.click(within(dialog).getByRole("button", { name: "复制" }));
    });
    expect(writeText).toHaveBeenCalledWith(preview);
    expect(
      stored()
        .filter((a) => a.exportedAt !== undefined)
        .map((a) => a.id),
    ).toEqual(["q1", "q2", "q3"]);
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("status").textContent).toContain("已复制 2 条");
  });

  it("复制失败时不标导出过，提示手动复制", async () => {
    writeText.mockRejectedValue(new Error("denied"));
    render(<NotesView entries={ENTRIES} />);
    fireEvent.click(screen.getByRole("button", { name: "导出给 Claude · 2 条" }));
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "复制" }));
    });
    expect(stored().find((a) => a.id === "q1")?.exportedAt).toBeUndefined();
    expect(screen.getByRole("dialog", { name: "导出给 Claude" })).toBeTruthy();
    expect(screen.getByRole("status").textContent).toContain("没能复制");
  });

  it("都导出过了，再导出时默认带上导出过的", () => {
    window.localStorage.setItem(
      ANNOTATIONS_KEY,
      JSON.stringify(SEED.map((a) => ({ ...a, exportedAt: 1 }))),
    );
    render(<NotesView entries={ENTRIES} />);
    fireEvent.click(screen.getByRole("button", { name: "导出给 Claude · 0 条" }));
    const box = screen.getByRole("checkbox", { name: /也带上导出过的/ }) as HTMLInputElement;
    expect(box.checked).toBe(true);
    expect(screen.getByTestId("export-preview").textContent).toContain("有问题 2 条");
  });
});
