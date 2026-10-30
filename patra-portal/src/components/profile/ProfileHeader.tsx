import { Link2 } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { ProfileHelpfulReceived } from "@/components/profile/ProfileHelpfulReceived";
import { Avatar } from "@/components/user/Avatar";
import {
  isSafeHref,
  joinedLabel,
  linkLabel,
  type PublicProfile,
} from "@/lib/identity/public-profile";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface ProfileHeaderProps {
  profile: PublicProfile;
  /// 本人视角：右上「编辑资料」
  self: boolean;
  /// 本人视角且邮箱未验证：链接位置写「验证邮箱后展示链接」
  linksPending: boolean;
}

/// 公开主页头部（简报 §3.13）：头像、昵称、@用户名、计数行、简介、链接、加入时间；本人视角右上「编辑资料」。
/// 计数行只有「获赞」（v0.15），在自己的 Suspense 里晚到、不挡头部其余内容；右上操作区是给以后留的位置。
export function ProfileHeader({ profile, self, linksPending }: ProfileHeaderProps) {
  const edit = self && (
    <Link href="/settings/profile" className={cn(btnSecondary, "px-3.5 py-1.5 text-sm")}>
      编辑资料
    </Link>
  );
  const links = profile.links.filter((link) => isSafeHref(link.url));
  return (
    <header className="mx-auto grid max-w-page grid-cols-[128px_minmax(0,1fr)_auto] gap-8 border-b border-border-default px-gutter pt-14 pb-10 max-[880px]:grid-cols-[88px_minmax(0,1fr)] max-[880px]:gap-x-5 max-[880px]:gap-y-4 max-[880px]:pt-7 max-[880px]:pb-7">
      <Avatar identity={profile} size={128} className="max-[880px]:hidden" />
      <Avatar identity={profile} size={88} className="hidden max-[880px]:inline-flex" />
      <div className="min-w-0 max-[880px]:col-span-2 max-[880px]:row-start-2">
        <h1
          title={profile.displayName}
          className="line-clamp-2 font-serif text-4xl leading-heading font-medium tracking-tight break-all text-fg-1 max-[880px]:text-3xl"
        >
          {profile.displayName}
        </h1>
        <p className="mt-1.5 font-mono text-sm text-fg-3">@{profile.username}</p>
        <Suspense fallback={null}>
          <ProfileHelpfulReceived userId={profile.userId} />
        </Suspense>
        {profile.bio && (
          <p className="mt-4.5 max-w-[38em] text-md leading-relaxed whitespace-pre-line text-fg-2">
            {profile.bio}
          </p>
        )}
        {links.length > 0 && (
          <ul className="mt-4 flex flex-wrap gap-x-4.5 gap-y-1.5 text-sm leading-5 max-[880px]:flex-col">
            {links.map((link) => (
              <li key={link.url}>
                <a
                  href={link.url}
                  target="_blank"
                  rel="nofollow noopener noreferrer ugc"
                  className="inline-flex items-center gap-1.5 text-link transition-colors hover:text-link-hover"
                >
                  <Link2 className="size-3.5" aria-hidden="true" />
                  <span className="link-draw">{linkLabel(link)}</span>
                </a>
              </li>
            ))}
          </ul>
        )}
        {linksPending && <p className="mt-3.5 text-sm text-fg-3">验证邮箱后展示链接</p>}
        <p className="mt-3.5 text-sm text-fg-3">{joinedLabel(profile.joinedAt)}</p>
      </div>
      {/* 窄屏时放到头像右侧同一行 */}
      <div className="max-[880px]:col-start-2 max-[880px]:row-start-1 max-[880px]:justify-self-end">
        {edit}
      </div>
    </header>
  );
}
