import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TrustTurnWidget } from "@/components/auth/human-verification/TrustTurnWidget";

const ORIGIN = "https://api.trustturn.cc";
const handlers = { onToken: vi.fn(), onClear: vi.fn(), onLoadError: vi.fn() };

function renderWidget(resetKey = 0) {
  return render(
    <TrustTurnWidget siteKey="site 1" action="password_reset" resetKey={resetKey} {...handlers} />,
  );
}

const frame = () => screen.getByTitle("TRUSTTURN 人机验证") as HTMLIFrameElement;

/// 模拟组件发来一条消息
function post(data: unknown, origin = ORIGIN, source: Window | null = frame().contentWindow) {
  act(() => {
    window.dispatchEvent(new MessageEvent("message", { data, origin, source }));
  });
}

const verified = (ticket: string) => ({
  type: "trustturn:verified",
  detail: { verificationTicket: ticket },
});

afterEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("TrustTurnWidget", () => {
  it("iframe 固定高度为 65px", () => {
    renderWidget();

    expect(frame().classList).toContain("h-[65px]");
  });

  it("iframe 带 sandbox，地址里的参数做了编码", () => {
    renderWidget();

    expect(frame().getAttribute("sandbox")).toBe("allow-scripts allow-same-origin allow-forms");
    const url = new URL(frame().src);
    expect(url.origin + url.pathname).toBe(`${ORIGIN}/widget.html`);
    expect(url.searchParams.get("site_key")).toBe("site 1");
    expect(url.searchParams.get("action")).toBe("password_reset");
  });

  it("页面不加载它域名下的脚本", () => {
    renderWidget();

    expect(document.querySelector('script[src*="trustturn"]')).toBeNull();
  });

  it("收到 iframe 发来的票据就交给表单", () => {
    renderWidget();

    post(verified("ticket-1"));

    expect(handlers.onToken).toHaveBeenCalledWith("ticket-1");
  });

  it("别的来源发来的消息被忽略", () => {
    renderWidget();

    post(verified("ticket-1"), "https://evil.example.com");

    expect(handlers.onToken).not.toHaveBeenCalled();
  });

  it("别的窗口发来的消息被忽略，即使 origin 对得上", () => {
    renderWidget();

    post(verified("ticket-1"), ORIGIN, window);

    expect(handlers.onToken).not.toHaveBeenCalled();
  });

  it.each([
    [{ type: "other", detail: { verificationTicket: "x" } }],
    [{ type: "trustturn:verified" }],
    [{ type: "trustturn:verified", detail: { verificationTicket: 1 } }],
    ["trustturn:verified"],
  ])("形状不对的消息 %j 被忽略", (data) => {
    renderWidget();

    post(data);

    expect(handlers.onToken).not.toHaveBeenCalled();
  });

  it("拿到票据 55 秒后清掉并重新加载", () => {
    vi.useFakeTimers();
    renderWidget();
    const before = frame();
    post(verified("ticket-1"));

    act(() => {
      vi.advanceTimersByTime(55_000);
    });

    expect(handlers.onClear).toHaveBeenCalledOnce();
    expect(frame()).not.toBe(before);
  });

  it("resetKey 变了就重新加载", () => {
    const view = renderWidget(0);
    const before = frame();

    view.rerender(
      <TrustTurnWidget siteKey="site 1" action="password_reset" resetKey={1} {...handlers} />,
    );

    expect(frame()).not.toBe(before);
  });

  it("15 秒没收到票据时提示加载失败，之后收到票据照常交给表单", () => {
    vi.useFakeTimers();
    renderWidget();

    act(() => {
      vi.advanceTimersByTime(15_000);
    });
    expect(handlers.onLoadError).toHaveBeenCalledOnce();

    post(verified("ticket-1"));
    expect(handlers.onToken).toHaveBeenCalledWith("ticket-1");
  });

  it("收到票据后不再提示加载失败", () => {
    vi.useFakeTimers();
    renderWidget();
    post(verified("ticket-1"));

    act(() => {
      vi.advanceTimersByTime(15_000);
    });

    expect(handlers.onLoadError).not.toHaveBeenCalled();
  });

  it("组件发来就绪消息后，用户迟迟不点也不提示加载失败", () => {
    vi.useFakeTimers();
    renderWidget();
    post({ type: "trustturn:state", state: "ready" });

    act(() => {
      vi.advanceTimersByTime(15_000);
    });

    expect(handlers.onLoadError).not.toHaveBeenCalled();
  });

  it("只到「验证中」、15 秒内没到就绪，仍然提示加载失败", () => {
    vi.useFakeTimers();
    renderWidget();
    post({ type: "trustturn:state", state: "verifying" });

    act(() => {
      vi.advanceTimersByTime(15_000);
    });

    expect(handlers.onLoadError).toHaveBeenCalledOnce();
  });
});
