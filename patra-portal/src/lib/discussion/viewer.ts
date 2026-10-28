import type { AvatarIdentity } from "@/components/user/Avatar";
import { useCurrentUser } from "@/lib/auth/current-user-query";

/// 编辑器要知道的访客：服务端首屏读到的会话（`Discussion` 里），水合后以客户端的当前用户为准
export type ViewerSeed =
  | { status: "anonymous" }
  | { status: "authenticated"; emailVerified: boolean; email: string; avatar: AvatarIdentity };

/// 当前访客：当前用户查询有结果就用它（退出登录、别的标签页验证了邮箱都会跟着变），还没取回时用服务端的初值
export function useViewer(seed: ViewerSeed): ViewerSeed {
  const current = useCurrentUser();
  if (current.status === "pending") {
    return seed;
  }
  if (current.status === "anonymous") {
    return { status: "anonymous" };
  }
  const { user } = current;
  return {
    status: "authenticated",
    emailVerified: user.emailVerified,
    email: user.email,
    avatar: {
      displayName: user.displayName,
      username: user.username,
      avatarUrl: user.avatarUrl,
      avatarPalette: user.avatarPalette,
    },
  };
}
