import { beforeEach, describe, expect, it } from "vitest";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";

describe("useBrowseFilterUiStore", () => {
  // 每个用例前固定为关闭态，避免共享单例的用例顺序耦合
  beforeEach(() => {
    useBrowseFilterUiStore.setState({ sheetOpen: false });
  });

  it("初始 sheetOpen 为 false", () => {
    const state = useBrowseFilterUiStore.getState();
    expect(state.sheetOpen).toBe(false);
  });

  it("open() 将 sheetOpen 设为 true", () => {
    useBrowseFilterUiStore.getState().open();
    expect(useBrowseFilterUiStore.getState().sheetOpen).toBe(true);
    // 清理
    useBrowseFilterUiStore.getState().close();
  });

  it("close() 将 sheetOpen 设为 false", () => {
    useBrowseFilterUiStore.getState().open();
    useBrowseFilterUiStore.getState().close();
    expect(useBrowseFilterUiStore.getState().sheetOpen).toBe(false);
  });

  it("toggle() 将 false → true", () => {
    useBrowseFilterUiStore.setState({ sheetOpen: false });
    useBrowseFilterUiStore.getState().toggle();
    expect(useBrowseFilterUiStore.getState().sheetOpen).toBe(true);
    // 清理
    useBrowseFilterUiStore.getState().close();
  });

  it("toggle() 将 true → false", () => {
    useBrowseFilterUiStore.setState({ sheetOpen: true });
    useBrowseFilterUiStore.getState().toggle();
    expect(useBrowseFilterUiStore.getState().sheetOpen).toBe(false);
  });
});
