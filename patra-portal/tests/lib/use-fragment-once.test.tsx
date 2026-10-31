import { render, screen } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { useFragmentOnce } from "@/lib/use-fragment-once";

const TOKEN = "t".repeat(43);

afterEach(() => {
  window.history.replaceState(null, "", "/");
  vi.restoreAllMocks();
});

/// 把 hook 的结果写进页面：还没挂载 / 读到了什么
function Probe({ parse }: { parse: (hash: string) => string | null }) {
  const value = useFragmentOnce(parse);
  return <p>{value === undefined ? "reading" : (value ?? "none")}</p>;
}

describe("useFragmentOnce", () => {
  it("严格模式下也只解析一次：拿到片段里的值，地址栏抹掉片段、保留路径与查询串", async () => {
    window.history.replaceState(null, "", `/x?y=1#token=${TOKEN}`);
    const parse = vi.fn((hash: string) => new URLSearchParams(hash.slice(1)).get("token"));
    render(
      <StrictMode>
        <Probe parse={parse} />
      </StrictMode>,
    );
    expect(await screen.findByText(TOKEN)).toBeInTheDocument();
    expect(parse).toHaveBeenCalledTimes(1);
    expect(`${window.location.pathname}${window.location.search}`).toBe("/x?y=1");
    expect(window.location.hash).toBe("");
  });

  it("没有片段：返回解析空串的结果，不改地址", async () => {
    window.history.replaceState(null, "", "/x");
    const replace = vi.spyOn(window.history, "replaceState");
    render(<Probe parse={() => null} />);
    expect(await screen.findByText("none")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
