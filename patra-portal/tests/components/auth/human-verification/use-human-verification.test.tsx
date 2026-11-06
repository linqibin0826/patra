import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useHumanVerification } from "@/components/auth/human-verification/use-human-verification";

describe("useHumanVerification", () => {
  it("被要求验证时，拿到令牌之前都算拦着", () => {
    const { result } = renderHook(() => useHumanVerification(true));
    expect(result.current.blocked).toBe(true);

    act(() => result.current.widgetProps.onToken("token-1"));

    expect(result.current.blocked).toBe(false);
    expect(result.current.token).toBe("token-1");
  });

  it("没被要求时不拦", () => {
    const { result } = renderHook(() => useHumanVerification(false));

    expect(result.current.required).toBe(false);
    expect(result.current.blocked).toBe(false);
  });

  it("demand 之后就要验证", () => {
    const { result } = renderHook(() => useHumanVerification(false));

    act(() => result.current.demand());

    expect(result.current.required).toBe(true);
    expect(result.current.blocked).toBe(true);
  });

  it("reset 清掉令牌并换一个 resetKey，按钮回到拦着", () => {
    const { result } = renderHook(() => useHumanVerification(true));
    act(() => result.current.widgetProps.onToken("token-1"));
    const before = result.current.widgetProps.resetKey;

    act(() => result.current.reset());

    expect(result.current.token).toBeNull();
    expect(result.current.blocked).toBe(true);
    expect(result.current.widgetProps.resetKey).not.toBe(before);
  });

  it("令牌过期时清掉，不换 resetKey", () => {
    const { result } = renderHook(() => useHumanVerification(true));
    act(() => result.current.widgetProps.onToken("token-1"));
    const before = result.current.widgetProps.resetKey;

    act(() => result.current.widgetProps.onClear());

    expect(result.current.blocked).toBe(true);
    expect(result.current.widgetProps.resetKey).toBe(before);
  });

  it("加载失败后拿到令牌，提示消失", () => {
    const { result } = renderHook(() => useHumanVerification(true));

    act(() => result.current.widgetProps.onLoadError());
    expect(result.current.loadFailed).toBe(true);

    act(() => result.current.widgetProps.onToken("token-1"));
    expect(result.current.loadFailed).toBe(false);
  });
});
