import { afterEach, describe, expect, it } from "vitest";
import { useAuthHandoffStore } from "@/store/auth-handoff";

afterEach(() => useAuthHandoffStore.getState().take());

describe("useAuthHandoffStore", () => {
  it("put 之后 take 读到一次，再读为空", () => {
    useAuthHandoffStore.getState().put({ email: "a@b.com", notice: "password-reset" });
    expect(useAuthHandoffStore.getState().take()).toEqual({
      email: "a@b.com",
      notice: "password-reset",
    });
    expect(useAuthHandoffStore.getState().take()).toEqual({ email: null, notice: null });
  });
});
