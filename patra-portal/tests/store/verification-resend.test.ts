import { afterEach, describe, expect, it } from "vitest";
import { useVerificationResendStore } from "@/store/verification-resend";

afterEach(() => {
  useVerificationResendStore.getState().reset();
});

describe("useVerificationResendStore", () => {
  it("开始冷却：记下可重发的时刻，不是限流", () => {
    useVerificationResendStore.getState().startCooldown(1_000);
    expect(useVerificationResendStore.getState()).toMatchObject({
      availableAt: 1_000,
      limited: false,
    });
  });

  it("限流：记下可重发的时刻，标成限流", () => {
    useVerificationResendStore.getState().markLimited(2_000);
    expect(useVerificationResendStore.getState()).toMatchObject({
      availableAt: 2_000,
      limited: true,
    });
  });

  it("重置：回到可发送", () => {
    useVerificationResendStore.getState().markLimited(2_000);
    useVerificationResendStore.getState().reset();
    expect(useVerificationResendStore.getState()).toMatchObject({
      availableAt: null,
      limited: false,
    });
  });
});
