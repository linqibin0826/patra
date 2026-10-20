import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { CitationPopover } from "@/components/portal/fulltext/CitationPopover";
import { ReferenceList, type ReferenceListItem } from "@/components/portal/fulltext/ReferenceList";

const items: ReferenceListItem[] = Array.from({ length: 10 }, (_, i) => ({
  anchorId: `ref-r${i + 1}`,
  refId: `r${i + 1}`,
  label: `${i + 1}`,
  content: `Author ${i + 1}. Title ${i + 1}.`,
  doi: i === 6 ? "10.1/seven" : null,
  pmid: i === 6 ? "777" : null,
  citeCount: 1,
}));

function renderPage() {
  return render(
    <>
      <div data-ft-body>
        <p>
          Risk rose.
          <sup>
            <a id="cite-1" href="#ref-r7" data-cite-ref="r7" data-cite-refs="r7">
              7
            </a>
          </sup>{" "}
          Others.
          <sup>
            <a id="cite-2" href="#ref-r1" data-cite-ref="r1" data-cite-refs="r1 r2 r3">
              1–3
            </a>
          </sup>
        </p>
      </div>
      <ReferenceList items={items} firstCiteByRef={{ r7: "cite-1", r1: "cite-2" }} />
      <CitationPopover items={items} />
    </>,
  );
}

afterEach(() => {
  window.history.replaceState(null, "", "/");
});

describe("CitationPopover", () => {
  it("点引用号弹出卡片：标签、著录、DOI、PubMed、「跳到文末」；地址不变", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("link", { name: "7" }));
    const card = await screen.findByRole("dialog");
    expect(within(card).getByText("参考文献 7")).toBeInTheDocument();
    expect(within(card).getByText("Author 7. Title 7.")).toBeInTheDocument();
    expect(within(card).getByRole("link", { name: "DOI ↗" })).toHaveAttribute(
      "href",
      "https://doi.org/10.1/seven",
    );
    expect(within(card).getByRole("link", { name: "PubMed ↗" })).toBeInTheDocument();
    expect(within(card).getByRole("link", { name: "跳到文末" })).toBeInTheDocument();
    expect(window.location.hash).toBe("");
  });

  it("Esc 关闭卡片，焦点回到引用号", async () => {
    const user = userEvent.setup();
    renderPage();
    const cite = screen.getByRole("link", { name: "7" });
    await user.click(cite);
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(cite).toHaveFocus();
  });

  it("键盘聚焦引用号后按 Enter 打开", async () => {
    const user = userEvent.setup();
    renderPage();
    screen.getByRole("link", { name: "7" }).focus();
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("一个引用指向 3 条时卡片里列出 3 条", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("link", { name: "1–3" }));
    const card = await screen.findByRole("dialog");
    expect(within(card).getAllByText(/^参考文献 \d$/)).toHaveLength(3);
  });

  it("「跳到文末」关闭卡片，展开收起区里的条目并跳过去，「↩」回到这处引用", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("link", { name: "7" }));
    const card = await screen.findByRole("dialog");
    await user.click(within(card).getByRole("link", { name: "跳到文末" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    const target = document.getElementById("ref-r7") as HTMLElement;
    expect(target).toBeVisible();
    expect(window.location.hash).toBe("#ref-r7");
    expect(within(target).getByRole("link", { name: /↩/ })).toHaveAttribute("href", "#cite-1");
  });
});
