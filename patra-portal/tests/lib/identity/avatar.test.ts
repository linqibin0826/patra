import { describe, expect, it } from "vitest";
import {
  AVATAR_PALETTE,
  avatarInitial,
  avatarPaletteClass,
  avatarSrc,
} from "@/lib/identity/avatar";

describe("avatarInitial", () => {
  it.each([
    ["陈宇", "chen-yu", "陈"],
    ["·小林", "x", "小"],
    ["  alice", "a", "A"],
    ["9号", "n", "9"],
    ["😀abc", "u", "😀"],
    ["「张」", "z", "张"],
    ["···", "zed", "Z"],
  ])("昵称 %j、用户名 %j → %j", (displayName, username, initial) => {
    expect(avatarInitial(displayName, username)).toBe(initial);
  });

  it("skips_leading_middle_dot", () => {
    expect(avatarInitial("·小林", "x")).toBe("小");
  });
});

describe("avatarSrc", () => {
  const URL = "/users/352303128713027814/avatar?v=0123456789abcdef";

  it("换成门户同源地址并追加尺寸", () => {
    expect(avatarSrc(URL, 128)).toBe(
      "/api/users/352303128713027814/avatar?v=0123456789abcdef&size=128",
    );
    expect(avatarSrc(URL, 512)).toBe(
      "/api/users/352303128713027814/avatar?v=0123456789abcdef&size=512",
    );
  });

  it.each([
    [null],
    ["/users/chen-yu/avatar?v=0123456789abcdef"],
    ["/users/0123/avatar?v=0123456789abcdef"],
    ["/users/352303128713027814/avatar?v=xyz"],
    ["/users/352303128713027814/avatar?v=0123456789ABCDEF"],
    ["/users/a/b/avatar?v=0123456789abcdef"],
    ["https://evil.example/users/a/avatar?v=0123456789abcdef"],
    ["/users/352303128713027814/avatar?v=0123456789abcdef&size=512"],
  ])("形状不对（%j）时为 null", (url) => {
    expect(avatarSrc(url, 128)).toBeNull();
  });
});

describe("调色板", () => {
  it("12 个色号，互不重复，按简报 §3.20 的顺序", () => {
    expect(AVATAR_PALETTE).toHaveLength(12);
    expect(new Set(AVATAR_PALETTE).size).toBe(12);
    expect(AVATAR_PALETTE[0]).toBe("bg-clay-700");
    expect(AVATAR_PALETTE[11]).toBe("bg-ink-600");
  });

  it("越界的色号取模", () => {
    expect(avatarPaletteClass(13)).toBe(AVATAR_PALETTE[1]);
    expect(avatarPaletteClass(-1)).toBe(AVATAR_PALETTE[11]);
  });
});
