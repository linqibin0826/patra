import { UserRound } from "lucide-react";
import { type AvatarSize, MOBILE_SIZE_CLASS, SIZE_CLASS } from "@/components/user/Avatar";
import { cn } from "@/lib/utils";

interface NeutralAvatarProps {
  /// `anonymous`：匿名作者，中间人形图标；`deleted`：已注销用户，无字
  kind: "anonymous" | "deleted";
  size: AvatarSize;
  mobileSize?: AvatarSize;
  className?: string;
}

/// 中性头像（简报 §3.20）：灰底，没有首字与颜色，一眼看出「这不是一个可识别的人」。匿名作者、已注销用户用
export function NeutralAvatar({ kind, size, mobileSize, className }: NeutralAvatarProps) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full bg-ink-200",
        SIZE_CLASS[size],
        mobileSize && MOBILE_SIZE_CLASS[mobileSize],
        className,
      )}
    >
      {kind === "anonymous" && (
        <UserRound className="size-3/5 text-fg-3" strokeWidth={1.75} aria-hidden="true" />
      )}
    </span>
  );
}
