import { afterEach, describe, expect, it } from "vitest";
import { useReauthStore } from "@/store/reauth";

afterEach(() => useReauthStore.getState().settle(false));

describe("useReauthStore", () => {
  it("request 打开对话框；已有待决请求时返回同一个 promise", async () => {
    const first = useReauthStore.getState().request();
    const second = useReauthStore.getState().request();
    expect(second).toBe(first);
    expect(useReauthStore.getState().open).toBe(true);
    useReauthStore.getState().settle(true);
    await expect(first).resolves.toBe(true);
    expect(useReauthStore.getState().open).toBe(false);
  });

  it("settle(false) 让等待的人拿到 false；之后的 request 是新的", async () => {
    const first = useReauthStore.getState().request();
    useReauthStore.getState().settle(false);
    await expect(first).resolves.toBe(false);
    const next = useReauthStore.getState().request();
    expect(next).not.toBe(first);
  });

  it("没有待决请求时 settle 什么也不做", () => {
    expect(() => useReauthStore.getState().settle(true)).not.toThrow();
    expect(useReauthStore.getState().open).toBe(false);
  });
});
