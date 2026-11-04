import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SignedInToast } from "@/components/auth/SignedInToast";

vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

afterEach(() => {
  // biome-ignore lint/suspicious/noDocumentCookie: 被测代码读的就是 document.cookie
  document.cookie = "patra_oauth_signed_in=; Path=/; Max-Age=0";
  vi.clearAllMocks();
});

describe("SignedInToast", () => {
  it("有标记：弹一次已登录，并清掉标记", () => {
    // biome-ignore lint/suspicious/noDocumentCookie: 被测代码读的就是 document.cookie
    document.cookie = "patra_oauth_signed_in=1; Path=/";
    render(<SignedInToast />);
    expect(toast.success).toHaveBeenCalledExactlyOnceWith("已登录");
    expect(document.cookie).not.toContain("patra_oauth_signed_in");
    render(<SignedInToast />);
    expect(toast.success).toHaveBeenCalledTimes(1);
  });

  it("没有标记：什么也不做", () => {
    render(<SignedInToast />);
    expect(toast.success).not.toHaveBeenCalled();
  });
});
