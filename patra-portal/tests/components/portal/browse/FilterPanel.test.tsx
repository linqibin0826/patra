import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FilterPanel } from "@/components/portal/browse/FilterPanel";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";

function renderPanel(side?: "left" | "bottom") {
  return render(
    <FilterPanel
      side={side}
      sheetTitle="筛选 · 已选 2 项"
      sheetFooter={<button type="button">查看结果</button>}
    >
      <p>筛选内容</p>
    </FilterPanel>,
  );
}

describe("FilterPanel", () => {
  beforeEach(() => useBrowseFilterUiStore.setState({ sheetOpen: false }));

  it("抽屉关闭时只渲染桌面侧栏", () => {
    renderPanel();
    expect(screen.getByText("筛选内容")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("抽屉打开：标题、内容、页脚都在，方向由 side 决定", async () => {
    useBrowseFilterUiStore.setState({ sheetOpen: true });
    renderPanel("bottom");
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveAttribute("data-side", "bottom");
    expect(within(dialog).getByText("筛选 · 已选 2 项")).toBeInTheDocument();
    expect(within(dialog).getByText("筛选内容")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "查看结果" })).toBeInTheDocument();
  });
});
