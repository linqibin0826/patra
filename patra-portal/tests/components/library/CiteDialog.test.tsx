import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQuery } from "@tests/support/render-with-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CiteDialog } from "@/components/library/CiteDialog";
import type { DialogScope } from "@/components/library/ExportDialog";

const fetchMock = vi.fn<typeof fetch>();
const writeText = vi.fn<(text: string) => Promise<void>>();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  window.localStorage.clear();
  writeText.mockResolvedValue();
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  writeText.mockReset();
});

const COLLECTION: DialogScope = {
  kind: "collection",
  id: "9",
  sort: "added",
  label: "收藏夹「组会」· 12 篇",
  name: "组会",
  count: 12,
};

const GBT = [
  { label: "[1]", text: "CHEN Y. Title one[J]. 2019." },
  { label: "[2]", text: "LI M. Title two[J]. 2020." },
];

function respond(keys: unknown, entries: unknown = GBT) {
  fetchMock.mockImplementation(async (url) =>
    String(url) === "/api/library/keys"
      ? Response.json(keys)
      : String(url) === "/api/publications/citations"
        ? Response.json({ entries })
        : new Response(null, { status: 404 }),
  );
}

function open(scope: DialogScope = COLLECTION) {
  const onExport = vi.fn();
  renderWithQuery(<CiteDialog open onOpenChange={() => {}} scope={scope} onExport={onExport} />);
  const user = userEvent.setup();
  // user-event 的 setup() 会换掉 navigator.clipboard：建好 user 再装桩
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
  return { onExport, user };
}

describe("CiteDialog", () => {
  it("标题带篇数；默认 GB/T 7714-2015，预览带序号", async () => {
    respond({ keys: ["11", "12"], skippedUnavailable: 0 });
    open();
    expect(screen.getByRole("dialog", { name: "复制引用 · 12 篇" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "GB/T 7714-2015" })).toBeChecked();
    expect(await screen.findByText("[1] CHEN Y. Title one[J]. 2019.")).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => url === "/api/publications/citations");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      ids: ["11", "12"],
      style: "gb-t-7714-2015",
    });
  });

  it("APA：没有序号，记住格式", async () => {
    respond({ keys: ["11"], skippedUnavailable: 0 }, [
      { label: null, text: "Chen, Y. (2019). Title." },
    ]);
    const { user } = open();
    await user.click(screen.getByRole("radio", { name: "APA" }));
    expect(await screen.findByText("Chen, Y. (2019). Title.")).toBeInTheDocument();
    expect(window.localStorage.getItem("patra.citation-style")).toBe("apa-7");
  });

  it("复制：按预览的文字复制", async () => {
    respond({ keys: ["11", "12"], skippedUnavailable: 0 });
    const { user } = open();
    await user.click(await screen.findByRole("button", { name: "复制 2 条引用" }));
    expect(writeText).toHaveBeenCalledWith(
      "[1] CHEN Y. Title one[J]. 2019.\n[2] LI M. Title two[J]. 2020.",
    );
  });

  it("单篇：标题与按钮都是「复制引用」，只复制正文不带序号", async () => {
    fetchMock.mockResolvedValue(Response.json({ entries: [GBT[0]] }));
    const { user } = open({
      kind: "items",
      label: "这一篇",
      name: "41605285",
      items: [{ key: "11", state: "ACTIVE" }],
    });
    expect(screen.getByRole("dialog", { name: "复制引用" })).toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "复制引用" }));
    expect(writeText).toHaveBeenCalledWith("CHEN Y. Title one[J]. 2019.");
  });

  it("超过 100 篇：不请求，说明并给导出入口", async () => {
    const { onExport, user } = open({ ...COLLECTION, label: "收藏夹「组会」· 230 篇", count: 230 });
    expect(
      screen.getByText(
        "一次最多复制 100 条引用。这个收藏夹有 230 篇，可以选中部分再复制，或者改用导出。",
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "导出…" }));
    expect(onExport).toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("选中的全是已失效的：说明没有可复制的引用，不一直显示加载，复制按钮不可用", async () => {
    respond({ keys: [], skippedUnavailable: 3 });
    open();
    expect(await screen.findByText("选中的文献都已失效，没有可复制的引用。")).toBeInTheDocument();
    expect(screen.queryByText("3 篇已失效，不含在内。")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^复制/ })).toBeDisabled();
    expect(fetchMock.mock.calls.some(([url]) => url === "/api/publications/citations")).toBe(false);
  });

  it("空收藏夹：说明还没有文献，不一直显示加载", async () => {
    respond({ keys: [], skippedUnavailable: 0 });
    open({ ...COLLECTION, count: 0, label: "收藏夹「组会」· 0 篇" });
    expect(await screen.findByText("这里还没有文献，没有可复制的引用。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^复制/ })).toBeDisabled();
  });

  it("有已失效的：说明不含在内", async () => {
    respond({ keys: ["11", "12"], skippedUnavailable: 2 });
    open();
    expect(await screen.findByText("2 篇已失效，不含在内。")).toBeInTheDocument();
  });

  it("生成失败：提示与重试，复制按钮禁用", async () => {
    fetchMock.mockImplementation(async (url) =>
      String(url) === "/api/library/keys"
        ? Response.json({ keys: ["11"], skippedUnavailable: 0 })
        : new Response(null, { status: 502 }),
    );
    const { user } = open();
    expect(await screen.findByText("引用暂时生成不了")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /复制/ })).toBeDisabled();
    respond({ keys: ["11"], skippedUnavailable: 0 });
    await user.click(screen.getByRole("button", { name: "重试" }));
    await waitFor(() =>
      expect(screen.getByText("[1] CHEN Y. Title one[J]. 2019.")).toBeInTheDocument(),
    );
  });
});

describe("CiteDialog · 评审修复", () => {
  it("范围取不到时「重试」重新取范围", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 502 }));
    const { user } = open();
    expect(await screen.findByText("引用暂时生成不了")).toBeInTheDocument();
    respond({ keys: ["11", "12"], skippedUnavailable: 0 });
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByText("[1] CHEN Y. Title one[J]. 2019.")).toBeInTheDocument();
  });
});
