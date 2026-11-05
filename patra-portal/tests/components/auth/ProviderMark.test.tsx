import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProviderMark } from "@/components/auth/ProviderMark";

function mark(provider: "google" | "github" | "orcid") {
  const { container } = render(<ProviderMark provider={provider} />);
  return container.querySelector("img") as HTMLImageElement;
}

describe("ProviderMark", () => {
  it("Google 用官方 SVG，文件就是官方的 40px 底板", () => {
    expect(mark("google")).toHaveProperty("src", expect.stringContaining("/oauth/google.svg"));
    const svg = readFileSync(join(__dirname, "../../../public/oauth/google.svg"), "utf8");
    expect(svg).toContain('width="40" height="40" viewBox="0 0 40 40"');
  });

  it("Google 不缩放，按原始大小居中裁出中间 20px 的 G：缩放后 G 只剩一半，还多一圈描边", () => {
    const image = mark("google");
    expect(image.className).toContain("object-none");
    expect(image.className).not.toContain("object-contain");
    expect([image.getAttribute("width"), image.getAttribute("height")]).toEqual(["20", "20"]);
  });

  it.each(["github", "orcid"] as const)("%s 整张缩放到 20px", (provider) => {
    expect(mark(provider).className).toContain("object-contain");
  });

  it("标识是装饰，alt 为空", () => {
    expect(mark("orcid").getAttribute("alt")).toBe("");
  });
});
