import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useDiscussionLocation } from "@/lib/discussion/use-discussion-location";

let params = new URLSearchParams();
vi.mock("next/navigation", () => ({ useSearchParams: () => params }));

beforeEach(() => {
  window.history.replaceState(null, "", "/papers/1?from=search");
  params = new URLSearchParams(window.location.search);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useDiscussionLocation", () => {
  it("地址 → 视图", () => {
    params = new URLSearchParams("comment=5&sort=latest");
    expect(renderHook(() => useDiscussionLocation()).result.current.view).toEqual({
      mode: "single",
      commentId: "5",
    });
    params = new URLSearchParams("sort=latest");
    expect(renderHook(() => useDiscussionLocation()).result.current.view).toEqual({
      mode: "list",
      sort: "latest",
    });
    params = new URLSearchParams("comment=abc&sort=hot");
    expect(renderHook(() => useDiscussionLocation()).result.current.view).toEqual({
      mode: "list",
      sort: "asc",
    });
  });

  it("最有帮助写进地址，读得回来", () => {
    const replace = vi.spyOn(window.history, "replaceState");
    params = new URLSearchParams("sort=helpful");
    const { result } = renderHook(() => useDiscussionLocation());
    expect(result.current.view).toEqual({ mode: "list", sort: "helpful" });
    result.current.setSort("helpful");
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?from=search&sort=helpful");
  });

  it("排序走 replaceState，保留其他参数；正序时去掉 sort", () => {
    const replace = vi.spyOn(window.history, "replaceState");
    const { result } = renderHook(() => useDiscussionLocation());
    result.current.setSort("latest");
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?from=search&sort=latest");
    result.current.setSort("asc");
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?from=search");
  });

  it("打开单楼走 pushState（可以后退）；查看全部讨论走 replaceState", () => {
    const push = vi.spyOn(window.history, "pushState");
    const replace = vi.spyOn(window.history, "replaceState");
    const { result } = renderHook(() => useDiscussionLocation());
    result.current.openSingle("9");
    expect(push).toHaveBeenLastCalledWith(null, "", "/papers/1?from=search&comment=9");
    result.current.showAll();
    expect(replace).toHaveBeenLastCalledWith(null, "", "/papers/1?from=search");
  });
});
