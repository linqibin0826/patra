import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Avatar } from "@/components/user/Avatar";

const IDENTITY = {
  displayName: "陈宇",
  username: "chen-yu",
  avatarUrl: "/users/352303128713027814/avatar?v=0123456789abcdef",
  avatarPalette: 1,
};

describe("Avatar", () => {
  it("有头像：小尺寸用 128 档图，图片不读名字", () => {
    const { container } = render(<Avatar identity={IDENTITY} size={36} />);
    const img = container.querySelector("img");
    expect(img).toHaveAttribute(
      "src",
      "/api/users/352303128713027814/avatar?v=0123456789abcdef&size=128",
    );
    expect(img).toHaveAttribute("alt", "");
  });

  it("72px 及以上用 512 档图", () => {
    const { container } = render(<Avatar identity={IDENTITY} size={96} />);
    expect(container.querySelector("img")?.getAttribute("src")).toContain("size=512");
  });

  it("图片加载失败时回落到默认头像", () => {
    const { container } = render(<Avatar identity={IDENTITY} size={40} />);
    fireEvent.error(container.querySelector("img") as HTMLImageElement);
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("陈")).toBeInTheDocument();
  });

  it("没有头像：首字 + 调色板底色，字对读屏隐藏", () => {
    const { container } = render(<Avatar identity={{ ...IDENTITY, avatarUrl: null }} size={128} />);
    const letter = screen.getByText("陈");
    expect(letter).toHaveAttribute("aria-hidden", "true");
    expect(container.firstElementChild).toHaveClass("bg-slate-500", "size-32", "text-5xl");
  });

  it("讨论区的尺寸：桌面 32、窄屏 28", () => {
    const { container } = render(
      <Avatar identity={{ ...IDENTITY, avatarUrl: null }} size={32} mobileSize={28} />,
    );
    expect(container.firstElementChild).toHaveClass("size-8", "max-[880px]:size-7");
  });

  it("主页对访客不可见（没有用户名）：首字取昵称", () => {
    render(<Avatar identity={{ ...IDENTITY, username: null, avatarUrl: null }} size={24} />);
    expect(screen.getByText("陈")).toBeInTheDocument();
  });
});
