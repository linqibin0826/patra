import "@testing-library/jest-dom/vitest";
import { renderNotifications } from "@tests/support/notifications";
import { afterEach, describe, expect, it, vi } from "vitest";
import { NotificationStream } from "@/components/notifications/NotificationStream";

afterEach(() => vi.unstubAllGlobals());

describe("NotificationStream", () => {
  it("不渲染任何东西，挂上就连推送", () => {
    const urls: string[] = [];
    vi.stubGlobal(
      "EventSource",
      class {
        static readonly CLOSED = 2;
        readyState = 0;
        constructor(url: string) {
          urls.push(url);
        }
        addEventListener() {}
        close() {}
      },
    );
    const { container } = renderNotifications(<NotificationStream />);
    expect(container).toBeEmptyDOMElement();
    expect(urls).toEqual(["/api/me/notifications/stream"]);
  });
});
