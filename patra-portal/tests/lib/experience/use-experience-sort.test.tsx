import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useExperienceSort } from "@/lib/experience/use-experience-sort";

let params = new URLSearchParams();
vi.mock("next/navigation", () => ({ useSearchParams: () => params }));

beforeEach(() => {
  window.history.replaceState(null, "", "/journals/100?from=list#experiences");
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useExperienceSort", () => {
  it("地址 → 排序", () => {
    params = new URLSearchParams("sort=helpful");
    expect(renderHook(() => useExperienceSort()).result.current.sort).toBe("helpful");
    params = new URLSearchParams("sort=hot");
    expect(renderHook(() => useExperienceSort()).result.current.sort).toBe("latest");
  });

  it("切换写进地址（replaceState），保留其他参数与 #experiences；最新投稿时去掉 sort", () => {
    const replace = vi.spyOn(window.history, "replaceState");
    const { result } = renderHook(() => useExperienceSort());
    result.current.setSort("helpful");
    expect(replace).toHaveBeenLastCalledWith(
      null,
      "",
      "/journals/100?from=list&sort=helpful#experiences",
    );
    result.current.setSort("latest");
    expect(replace).toHaveBeenLastCalledWith(null, "", "/journals/100?from=list#experiences");
  });
});
