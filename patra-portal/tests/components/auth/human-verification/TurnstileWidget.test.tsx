import { act, render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TurnstileWidget as Widget } from "@/components/auth/human-verification/TurnstileWidget";

const SCRIPT = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

interface RenderOptions {
  sitekey: string;
  action: string;
  language: string;
  callback: (token: string) => void;
  "expired-callback": () => void;
  "error-callback": () => void;
}

const api = {
  render: vi.fn<(element: HTMLElement, options: RenderOptions) => string>(() => "widget-1"),
  reset: vi.fn<(id: string) => void>(),
  remove: vi.fn<(id: string) => void>(),
};

const handlers = { onToken: vi.fn(), onClear: vi.fn(), onLoadError: vi.fn() };

let TurnstileWidget: typeof Widget;

const scripts = () => [...document.querySelectorAll(`script[src="${SCRIPT}"]`)];

/// 让页面里的脚本标签加载成功
function scriptLoaded() {
  window.turnstile = api;
  for (const script of scripts()) {
    act(() => {
      script.dispatchEvent(new Event("load"));
    });
  }
}

function renderWidget(resetKey = 0) {
  return render(
    <TurnstileWidget siteKey="site-1" action="login" resetKey={resetKey} {...handlers} />,
  );
}

/// 第一次渲染时的参数
const options = () => {
  const call = api.render.mock.calls[0];
  if (!call) {
    throw new Error("还没有渲染");
  }
  return call[1];
};

beforeEach(async () => {
  vi.resetModules();
  ({ TurnstileWidget } = await import("@/components/auth/human-verification/TurnstileWidget"));
});

afterEach(() => {
  for (const script of scripts()) {
    script.remove();
  }
  delete window.turnstile;
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("TurnstileWidget", () => {
  it("脚本加载后按站点标识、操作和语言渲染", async () => {
    renderWidget();
    scriptLoaded();

    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());
    expect(options()).toMatchObject({ sitekey: "site-1", action: "login", language: "zh-cn" });
  });

  it("验证通过后把令牌交给表单", async () => {
    renderWidget();
    scriptLoaded();
    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());

    options().callback("token-1");

    expect(handlers.onToken).toHaveBeenCalledWith("token-1");
  });

  it("令牌过期时清掉", async () => {
    renderWidget();
    scriptLoaded();
    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());

    options()["expired-callback"]();

    expect(handlers.onClear).toHaveBeenCalledOnce();
  });

  it("resetKey 变了就重新验证", async () => {
    const view = renderWidget(0);
    scriptLoaded();
    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());
    expect(api.reset).not.toHaveBeenCalled();

    view.rerender(<TurnstileWidget siteKey="site-1" action="login" resetKey={1} {...handlers} />);

    expect(api.reset).toHaveBeenCalledWith("widget-1");
  });

  it("卸载时移除", async () => {
    const view = renderWidget();
    scriptLoaded();
    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());

    view.unmount();

    expect(api.remove).toHaveBeenCalledWith("widget-1");
  });

  it("组件报错算加载失败", async () => {
    renderWidget();
    scriptLoaded();
    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());

    options()["error-callback"]();

    expect(handlers.onLoadError).toHaveBeenCalledOnce();
  });

  it("脚本加载失败算加载失败", async () => {
    renderWidget();

    act(() => {
      scripts()[0]?.dispatchEvent(new Event("error"));
    });

    await waitFor(() => expect(handlers.onLoadError).toHaveBeenCalledOnce());
  });

  it("上次加载失败后，再次挂载会重新加载", async () => {
    const first = renderWidget();
    act(() => {
      scripts()[0]?.dispatchEvent(new Event("error"));
    });
    await waitFor(() => expect(handlers.onLoadError).toHaveBeenCalledOnce());
    first.unmount();

    renderWidget();
    scriptLoaded();

    await waitFor(() => expect(api.render).toHaveBeenCalledOnce());
  });

  it("渲染出来之前就预留高度，提交按钮不会被顶下去", () => {
    const { container } = renderWidget();

    expect(container.firstElementChild?.className).toContain("min-h-17.5");
  });

  it("两个组件只注入一次脚本", () => {
    renderWidget();
    renderWidget();

    expect(scripts()).toHaveLength(1);
  });

  it("15 秒没渲染完算加载失败", () => {
    vi.useFakeTimers();
    renderWidget();

    act(() => {
      vi.advanceTimersByTime(15_000);
    });

    expect(handlers.onLoadError).toHaveBeenCalledOnce();
  });
});
