import "@testing-library/jest-dom/vitest";
import { render, renderHook, screen } from "@testing-library/react";
import { act } from "react";
import { describe, expect, it, vi } from "vitest";
import { HumanVerificationWidget } from "@/components/auth/human-verification/HumanVerificationWidget";
import { useHumanVerification } from "@/components/auth/human-verification/use-human-verification";
import { VERIFICATION_MESSAGES } from "@/lib/auth/messages";
import type { HumanVerificationConfig } from "@/lib/auth/types";

vi.mock("@/components/auth/human-verification/TurnstileWidget", () => ({
  TurnstileWidget: () => <div>turnstile</div>,
}));
vi.mock("@/components/auth/human-verification/TrustTurnWidget", () => ({
  TrustTurnWidget: () => <div>trustturn</div>,
}));

const TURNSTILE: HumanVerificationConfig = { provider: "turnstile", siteKey: "site-1" };
const TRUST_TURN: HumanVerificationConfig = { provider: "trustturn", siteKey: "site-2" };

function state(required: boolean) {
  return renderHook(() => useHumanVerification(required)).result;
}

describe("HumanVerificationWidget", () => {
  it("没被要求时什么都不渲染，不加载第三方组件", () => {
    const { container } = render(
      <HumanVerificationWidget
        config={TURNSTILE}
        action="login"
        verification={state(false).current}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("配置还在读取时什么都不渲染", () => {
    const { container } = render(
      <HumanVerificationWidget
        config={undefined}
        action="login"
        verification={state(true).current}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("读不到配置时显示不可用", () => {
    render(
      <HumanVerificationWidget config={null} action="login" verification={state(true).current} />,
    );

    expect(screen.getByText(VERIFICATION_MESSAGES.unavailable)).toBeInTheDocument();
  });

  it("按厂商选实现", () => {
    const { rerender } = render(
      <HumanVerificationWidget
        config={TURNSTILE}
        action="login"
        verification={state(true).current}
      />,
    );
    expect(screen.getByText("turnstile")).toBeInTheDocument();

    rerender(
      <HumanVerificationWidget
        config={TRUST_TURN}
        action="login"
        verification={state(true).current}
      />,
    );
    expect(screen.getByText("trustturn")).toBeInTheDocument();
  });

  it("加载失败时显示提示", () => {
    const result = state(true);
    act(() => result.current.widgetProps.onLoadError());

    render(
      <HumanVerificationWidget config={TURNSTILE} action="login" verification={result.current} />,
    );

    expect(screen.getByText(VERIFICATION_MESSAGES.loadFailed)).toBeInTheDocument();
  });
});
