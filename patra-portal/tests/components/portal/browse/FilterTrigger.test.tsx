import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FilterTrigger } from "@/components/portal/browse/FilterTrigger";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";

describe("FilterTrigger", () => {
  beforeEach(() => useBrowseFilterUiStore.setState({ sheetOpen: false }));

  it("点击打开筛选抽屉", () => {
    render(<FilterTrigger count={0} />);
    fireEvent.click(screen.getByRole("button", { name: "筛选" }));
    expect(useBrowseFilterUiStore.getState().sheetOpen).toBe(true);
  });

  it("有已选条件时显示角标，无则不显示", () => {
    const { rerender } = render(<FilterTrigger count={3} />);
    expect(screen.getByText("3")).toBeInTheDocument();
    rerender(<FilterTrigger count={0} />);
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });
});
