import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQuery } from "@tests/support/render-with-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { toast } from "sonner";
import { type DialogScope, ExportDialog } from "@/components/library/ExportDialog";

vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  window.localStorage.clear();
  URL.createObjectURL = vi.fn(() => "blob:x");
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  vi.mocked(toast.success).mockReset();
});

const COLLECTION: DialogScope = {
  kind: "collection",
  id: "9",
  sort: "added",
  label: "收藏夹「组会」· 23 篇",
  name: "组会",
  count: 23,
};

/// 按地址回：keys 回 `keys`，导出回文件
function respond(keys: unknown, file: Response = fileResponse()) {
  fetchMock.mockImplementation(async (url) =>
    String(url) === "/api/library/keys" ? Response.json(keys) : file.clone(),
  );
}

function fileResponse() {
  return new Response("TY  - JOUR", {
    headers: {
      "Content-Disposition": "attachment; filename*=UTF-8''patra-%E7%BB%84%E4%BC%9A-2026-10-24.ris",
    },
  });
}

function open(scope: DialogScope = COLLECTION) {
  const onOpenChange = vi.fn();
  renderWithQuery(<ExportDialog open onOpenChange={onOpenChange} scope={scope} />);
  return { onOpenChange, user: userEvent.setup() };
}

describe("ExportDialog", () => {
  it("范围行、四种格式与适用软件，默认 RIS", async () => {
    respond({ keys: ["11"], skippedUnavailable: 0 });
    open();
    expect(screen.getByText("收藏夹「组会」· 23 篇")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /RIS/ })).toBeChecked();
    expect(screen.getByText("Zotero、EndNote、NoteExpress、Mendeley")).toBeInTheDocument();
    expect(screen.getByText("LaTeX、Overleaf")).toBeInTheDocument();
    expect(screen.getByText("Excel 等表格软件")).toBeInTheDocument();
    expect(screen.getByText("Zotero、Pandoc 等支持 CSL 的工具")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("button", { name: "下载 .ris" })).toBeEnabled());
  });

  it("记住上次的格式", async () => {
    respond({ keys: ["11"], skippedUnavailable: 0 });
    const { user } = open();
    await user.click(screen.getByRole("radio", { name: /BibTeX/ }));
    expect(screen.getByRole("button", { name: "下载 .bib" })).toBeInTheDocument();
    expect(window.localStorage.getItem("patra.export-format")).toBe("bibtex");
  });

  it("有已失效的：说明不会导出", async () => {
    respond({ keys: ["11"], skippedUnavailable: 2 });
    open();
    expect(await screen.findByText("2 篇已失效，不会导出。")).toBeInTheDocument();
  });

  it("整个文献库超过 5,000 篇：提示并禁用下载，不取 keys", async () => {
    open({
      kind: "library",
      view: "all",
      sort: "added",
      label: "整个文献库 · 12,480 篇",
      name: "library",
      count: 12480,
    });
    expect(
      screen.getByText(
        "一次最多导出 5,000 篇。整个文献库有 12,480 篇，可以分别导出各个收藏夹和「未分类」。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "下载 .ris" })).toBeDisabled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("未分类超过 5,000 篇：改为提示在列表里选中", () => {
    open({
      kind: "library",
      view: "unfiled",
      sort: "added",
      label: "未分类 · 6,000 篇",
      name: "未分类",
      count: 6000,
    });
    expect(
      screen.getByText("一次最多导出 5,000 篇。未分类有 6,000 篇，可以在列表里选中部分再导出。"),
    ).toBeInTheDocument();
  });

  it("下载：交给 catalog 导出，关闭并 toast 文件名", async () => {
    respond({ keys: ["11", "12"], skippedUnavailable: 0 });
    const { onOpenChange, user } = open();
    await waitFor(() => expect(screen.getByRole("button", { name: "下载 .ris" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "下载 .ris" }));
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(toast.success).toHaveBeenCalledWith("已开始下载 patra-组会-2026-10-24.ris");
    const call = fetchMock.mock.calls.find(([url]) => url === "/api/publications/export");
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({
      ids: ["11", "12"],
      format: "ris",
      name: "组会",
    });
  });

  it("下载失败：不关，提示", async () => {
    respond({ keys: ["11"], skippedUnavailable: 0 }, new Response(null, { status: 502 }));
    const { onOpenChange, user } = open();
    await waitFor(() => expect(screen.getByRole("button", { name: "下载 .ris" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "下载 .ris" }));
    expect(await screen.findByText("导出没有成功，请稍后再试")).toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("选中的几篇：直接用条目的 key，跳过已失效，不取 keys", async () => {
    fetchMock.mockResolvedValue(fileResponse());
    const { user } = open({
      kind: "items",
      label: "选中的 3 篇",
      name: "selection",
      items: [
        { key: "11", state: "ACTIVE" },
        { key: null, state: "UNAVAILABLE" },
        { key: "12", state: "ACTIVE" },
      ],
    });
    expect(screen.getByText("1 篇已失效，不会导出。")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "下载 .ris" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toMatchObject({
      ids: ["11", "12"],
      name: "selection",
    });
  });
});
