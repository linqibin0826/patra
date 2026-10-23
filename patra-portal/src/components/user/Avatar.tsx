"use client";

import { useState } from "react";
import { avatarInitial, avatarPaletteClass, avatarSrc } from "@/lib/identity/avatar";
import { cn } from "@/lib/utils";

/// 画头像要用的身份信息，`/auth/me` 与公开主页的响应都有
export interface AvatarIdentity {
  displayName: string;
  username: string;
  avatarUrl: string | null;
  avatarPalette: number;
}

type AvatarSize = 36 | 40 | 72 | 88 | 96 | 128;

/// 每档的直径与默认头像的字号（约直径 × 0.42，取最近的字号阶，简报 §3.20）
const SIZE_CLASS: Record<AvatarSize, string> = {
  36: "size-9 text-md",
  40: "size-10 text-lg",
  72: "size-18 text-3xl",
  88: "size-22 text-4xl",
  96: "size-24 text-4xl",
  128: "size-32 text-5xl",
};

interface AvatarProps {
  identity: AvatarIdentity;
  size: AvatarSize;
  className?: string;
}

/// 圆形头像：有上传的头像就显示图片（36 / 40px 取 128 档，其余 512 档），图片加载失败或没有头像时显示
/// 默认头像（首字 + 按色号取的底色）。图片 `alt` 为空、默认头像的字对读屏隐藏：旁边总有昵称。
export function Avatar({ identity, size, className }: AvatarProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const src = avatarSrc(identity.avatarUrl, size >= 72 ? 512 : 128);
  const showImage = src !== null && src !== failedSrc;
  return (
    <span
      className={cn(
        "inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full font-serif leading-none font-medium text-paper-50",
        SIZE_CLASS[size],
        avatarPaletteClass(identity.avatarPalette),
        className,
      )}
    >
      {showImage ? (
        // biome-ignore lint/performance/noImgElement: 头像经门户自己的转发接口按两档尺寸出图，不经 next/image 再处理
        <img
          src={src}
          alt=""
          className="size-full object-cover"
          onError={() => setFailedSrc(src)}
        />
      ) : (
        <span aria-hidden="true">{avatarInitial(identity.displayName, identity.username)}</span>
      )}
    </span>
  );
}
