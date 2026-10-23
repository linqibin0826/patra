import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { REAUTH_REQUIRED, type ReauthRequired } from "@/lib/identity/mutation-failure";
import { useReauth } from "@/lib/reauth/use-reauth";
import { useReauthStore } from "@/store/reauth";

afterEach(() => useReauthStore.getState().settle(false));

type Done = { ok: true; value: number };

describe("useReauth().withReauth", () => {
  it("不需要再认证：原样返回，只调用一次", async () => {
    const call = vi.fn(async (): Promise<Done | ReauthRequired> => ({ ok: true, value: 1 }));
    const { result } = renderHook(() => useReauth());
    await expect(result.current.withReauth(call)).resolves.toEqual({ ok: true, value: 1 });
    expect(call).toHaveBeenCalledTimes(1);
    expect(useReauthStore.getState().open).toBe(false);
  });

  it("需要再认证：弹框，验证成功后再调一次并返回第二次的结果", async () => {
    const call = vi
      .fn<() => Promise<Done | ReauthRequired>>()
      .mockResolvedValueOnce(REAUTH_REQUIRED)
      .mockResolvedValueOnce({ ok: true, value: 2 });
    const { result } = renderHook(() => useReauth());
    const pending = result.current.withReauth(call);
    await vi.waitFor(() => expect(useReauthStore.getState().open).toBe(true));
    act(() => useReauthStore.getState().settle(true));
    await expect(pending).resolves.toEqual({ ok: true, value: 2 });
    expect(call).toHaveBeenCalledTimes(2);
  });

  it("取消：返回 cancelled，不再调用", async () => {
    const call = vi.fn(async (): Promise<Done | ReauthRequired> => REAUTH_REQUIRED);
    const { result } = renderHook(() => useReauth());
    const pending = result.current.withReauth(call);
    await vi.waitFor(() => expect(useReauthStore.getState().open).toBe(true));
    act(() => useReauthStore.getState().settle(false));
    await expect(pending).resolves.toEqual({ ok: false, reason: "cancelled" });
    expect(call).toHaveBeenCalledTimes(1);
  });

  it("重试后仍要求再认证：再弹一次，由用户决定", async () => {
    const call = vi
      .fn<() => Promise<Done | ReauthRequired>>()
      .mockResolvedValueOnce(REAUTH_REQUIRED)
      .mockResolvedValueOnce(REAUTH_REQUIRED)
      .mockResolvedValueOnce({ ok: true, value: 3 });
    const { result } = renderHook(() => useReauth());
    const pending = result.current.withReauth(call);
    await vi.waitFor(() => expect(useReauthStore.getState().open).toBe(true));
    act(() => useReauthStore.getState().settle(true));
    await vi.waitFor(() => expect(call).toHaveBeenCalledTimes(2));
    await vi.waitFor(() => expect(useReauthStore.getState().open).toBe(true));
    act(() => useReauthStore.getState().settle(true));
    await expect(pending).resolves.toEqual({ ok: true, value: 3 });
  });
});
