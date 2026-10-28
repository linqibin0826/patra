import Link from "next/link";
import { Avatar, type AvatarSize } from "@/components/user/Avatar";
import { NeutralAvatar } from "@/components/user/NeutralAvatar";
import type { AuthorView } from "@/lib/discussion/author";
import { cn } from "@/lib/utils";

/// 署名的头像（评论、经验页共用）：匿名作者中性头像、已注销灰底、其余是头像或默认头像
export function AuthorAvatar({
  view,
  size,
  mobileSize,
}: {
  view: AuthorView;
  size: AvatarSize;
  mobileSize?: AvatarSize;
}) {
  if (view.kind === "anonymous" || view.kind === "deleted") {
    return (
      <NeutralAvatar
        kind={view.kind === "anonymous" ? "anonymous" : "deleted"}
        size={size}
        mobileSize={mobileSize}
      />
    );
  }
  const { card } = view;
  return (
    <Avatar
      identity={{
        displayName: card.displayName ?? "",
        username: card.username,
        avatarUrl: card.avatarUrl,
        avatarPalette: card.avatarPalette,
      }}
      size={size}
      mobileSize={mobileSize}
    />
  );
}

/// 署名的名字（评论、经验页共用）：可见的链主页并跟 `@用户名`；主页不可见只有昵称；匿名、已注销是固定文字
export function AuthorName({ view, className }: { view: AuthorView; className?: string }) {
  const name = cn("text-sm font-medium text-fg-1", className);
  switch (view.kind) {
    case "anonymous":
      return (
        <span className={cn(name, "text-fg-2")}>{view.self ? "匿名作者（你）" : "匿名作者"}</span>
      );
    case "deleted":
      return <span className={cn(name, "font-normal text-fg-3")}>已注销用户</span>;
    case "hidden":
      return <span className={name}>{view.card.displayName}</span>;
    case "visible":
      return (
        <>
          <Link
            href={`/u/${view.card.username}`}
            className={cn(name, "underline-offset-3 hover:underline")}
          >
            {view.card.displayName}
          </Link>
          <span className="font-mono text-2xs text-fg-3">@{view.card.username}</span>
        </>
      );
  }
}
