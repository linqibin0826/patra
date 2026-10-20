import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FullTextTocSheet } from "@/components/portal/fulltext/FullTextTocSheet";

const ENTRIES = [
  { id: "ft-s1", number: "01", label: "Introduction", children: [] },
  { id: "ft-s2", number: "02", label: "Methods", children: [] },
];

afterEach(() => {
  document.getElementById("ft-s2")?.remove();
  window.history.replaceState(null, "", "/");
});

function renderSheet() {
  return render(
    <FullTextTocSheet
      entries={ENTRIES}
      declarationCount={2}
      referenceCount={10}
      readMin={25}
      paperId="42"
      hasPdf
      pdfSizeBytes="2500000"
      licenseText="CC BY 4.0"
      fetchedDate="2026-10-18"
    />,
  );
}

describe("FullTextTocSheet", () => {
  it("面包屑右端的「目录」按钮打开底部目录面板，面板底部同样能下载 PDF", async () => {
    const user = userEvent.setup();
    renderSheet();
    await user.click(screen.getByRole("button", { name: "目录" }));
    const dialog = await screen.findByRole("dialog", { name: "目录" });
    expect(within(dialog).getByRole("link", { name: "Methods" })).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: /下载 PDF/ })).toBeInTheDocument();
  });

  it("点目录项：收起面板，再跳到对应章节并更新地址栏", async () => {
    const user = userEvent.setup();
    const target = document.createElement("h2");
    target.id = "ft-s2";
    const scrollIntoView = vi.fn();
    target.scrollIntoView = scrollIntoView;
    document.body.append(target);
    renderSheet();

    await user.click(screen.getByRole("button", { name: "目录" }));
    const dialog = await screen.findByRole("dialog", { name: "目录" });
    await user.click(within(dialog).getByRole("link", { name: "Methods" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    await waitFor(() => expect(scrollIntoView).toHaveBeenCalled());
    expect(window.location.hash).toBe("#ft-s2");
  });
});
