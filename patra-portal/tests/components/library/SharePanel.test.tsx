import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQuery } from "@tests/support/render-with-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { toast } from "sonner";
import { ShareButton } from "@/components/library/SharePanel";

vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

const fetchMock = vi.fn<typeof fetch>();
const writeText = vi.fn<(text: string) => Promise<void>>();
const LINK = "http://mini/papers/1";
const CITATION = { label: null, text: "CHEN Y. Heart failure[J]. Lancet, 2019." };

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  window.localStorage.clear();
  writeText.mockResolvedValue();
  URL.createObjectURL = vi.fn(() => "blob:x");
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  writeText.mockReset();
  vi.mocked(toast.success).mockReset();
});

function fileResponse() {
  return new Response("TY  - JOUR", {
    headers: {
      "Content-Disposition": "attachment; filename*=UTF-8''patra-31535829-2026-10-24.ris",
    },
  });
}

/// 按地址回：引用回 `citations`（Response 或条目），导出回 `file`
function respond({
  citations = Response.json({ entries: [CITATION] }),
  file = fileResponse(),
}: {
  citations?: Response | (() => Promise<Response>);
  file?: Response | (() => Promise<Response>);
} = {}) {
  fetchMock.mockImplementation(async (url) => {
    const reply = String(url) === "/api/publications/citations" ? citations : file;
    return typeof reply === "function" ? reply() : reply.clone();
  });
}

function bodyOf(url: string, nth = 0) {
  const call = fetchMock.mock.calls.filter(([u]) => u === url)[nth];
  return JSON.parse(String(call?.[1]?.body));
}

async function openPanel(pmid: string | null = "31535829") {
  renderWithQuery(<ShareButton paperId="1" pmid={pmid} link={LINK} />);
  const user = userEvent.setup();
  // user-event 的 setup() 会换掉 navigator.clipboard：建好 user 再装桩
  Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
  await user.click(screen.getByRole("button", { name: "分享" }));
  return { user, panel: await screen.findByRole("dialog", { name: "分享" }) };
}

describe("SharePanel", () => {
  it("share.open：链接框是规范地址，默认 GB/T 7714-2015，预览单篇正文", async () => {
    respond();
    const { panel } = await openPanel();
    expect(within(panel).getByRole("textbox", { name: "链接" })).toHaveValue(LINK);
    expect(within(panel).getByRole("radio", { name: "GB/T 7714-2015" })).toBeChecked();
    expect(await within(panel).findByText(CITATION.text)).toBeInTheDocument();
    expect(bodyOf("/api/publications/citations")).toEqual({ ids: ["1"], style: "gb-t-7714-2015" });
    expect(within(panel).getByText("RIS 可导入 Zotero、EndNote、NoteExpress")).toBeInTheDocument();
  });

  it("share.format：切 APA 重新请求并记住；上次是 AMA 时打开即选中", async () => {
    respond();
    const { user, panel } = await openPanel();
    await within(panel).findByText(CITATION.text);
    await user.click(within(panel).getByRole("radio", { name: "APA" }));
    await waitFor(() => expect(bodyOf("/api/publications/citations", 1).style).toBe("apa-7"));
    expect(window.localStorage.getItem("patra.citation-style")).toBe("apa-7");
  });

  it("share.format：记住的格式", async () => {
    window.localStorage.setItem("patra.citation-style", "ama-11");
    respond();
    const { panel } = await openPanel();
    expect(within(panel).getByRole("radio", { name: "AMA" })).toBeChecked();
    await within(panel).findByText(CITATION.text);
    // 第一次请求就用记住的格式，不先白打一次 GB/T
    const calls = fetchMock.mock.calls.filter(([url]) => url === "/api/publications/citations");
    expect(calls).toHaveLength(1);
    expect(bodyOf("/api/publications/citations").style).toBe("ama-11");
  });

  it("share.cite.loading：骨架，复制引用禁用", async () => {
    respond({ citations: () => new Promise<Response>(() => {}) });
    const { panel } = await openPanel();
    expect(within(panel).getByRole("button", { name: "复制引用" })).toBeDisabled();
  });

  it("share.cite.error：引用暂时生成不了 + 重试", async () => {
    respond({ citations: new Response(null, { status: 500 }) });
    const { user, panel } = await openPanel();
    expect(await within(panel).findByText("引用暂时生成不了")).toBeInTheDocument();
    expect(within(panel).getByRole("button", { name: "复制引用" })).toBeDisabled();
    respond();
    await user.click(within(panel).getByRole("button", { name: "重试" }));
    expect(await within(panel).findByText(CITATION.text)).toBeInTheDocument();
  });

  it("copy.ok：复制引用只写正文，按钮内已复制", async () => {
    respond();
    const { user, panel } = await openPanel();
    await within(panel).findByText(CITATION.text);
    await user.click(within(panel).getByRole("button", { name: "复制引用" }));
    expect(writeText).toHaveBeenCalledWith(CITATION.text);
    expect(await within(panel).findByRole("button", { name: "已复制" })).toBeInTheDocument();
  });

  it("copy.fallback：剪贴板接口不可用时 execCommand 复制链接", async () => {
    respond();
    writeText.mockRejectedValue(new DOMException("insecure", "NotAllowedError"));
    const execCommand = vi.fn(() => true);
    Object.defineProperty(document, "execCommand", { value: execCommand, configurable: true });
    const { user, panel } = await openPanel();
    await user.click(within(panel).getByRole("button", { name: "复制链接" }));
    expect(execCommand).toHaveBeenCalledWith("copy");
    expect(await within(panel).findByRole("button", { name: "已复制" })).toBeInTheDocument();
  });

  it("share.export.downloading：准备中…，四个都禁用；完成 toast 文件名，文件名用 PMID", async () => {
    let finish: (res: Response) => void = () => {};
    respond({ file: () => new Promise<Response>((resolve) => (finish = resolve)) });
    const { user, panel } = await openPanel();
    await user.click(within(panel).getByRole("button", { name: "RIS" }));
    expect(within(panel).getByRole("button", { name: "准备中…" })).toBeDisabled();
    for (const name of ["BibTeX", "CSV", "CSL-JSON"]) {
      expect(within(panel).getByRole("button", { name })).toBeDisabled();
    }
    expect(bodyOf("/api/publications/export")).toEqual({
      ids: ["1"],
      format: "ris",
      name: "31535829",
    });
    finish(fileResponse());
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith("已开始下载 patra-31535829-2026-10-24.ris"),
    );
    expect(within(panel).getByRole("button", { name: "RIS" })).toBeEnabled();
  });

  it("没有 PMID 时文件名用文献 ID", async () => {
    respond();
    const { user, panel } = await openPanel(null);
    await user.click(within(panel).getByRole("button", { name: "BibTeX" }));
    await waitFor(() =>
      expect(bodyOf("/api/publications/export")).toEqual({
        ids: ["1"],
        format: "bibtex",
        name: "1",
      }),
    );
  });

  it("导出失败：面板顶部提示，按钮恢复", async () => {
    respond({ file: new Response(null, { status: 500 }) });
    const { user, panel } = await openPanel();
    await user.click(within(panel).getByRole("button", { name: "CSV" }));
    expect(await within(panel).findByText("导出没有成功，请稍后再试")).toBeInTheDocument();
    expect(within(panel).getByRole("button", { name: "CSV" })).toBeEnabled();
    expect(toast.success).not.toHaveBeenCalled();
  });
});
