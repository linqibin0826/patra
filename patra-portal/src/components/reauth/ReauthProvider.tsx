import { type ReactNode, Suspense } from "react";
import { ReauthDialog } from "@/components/reauth/ReauthDialog";

/// 挂再认证对话框（spec §6.2）：需要再认证的操作都在设置页里，所以包在设置页 layout 外层。对话框读查询串拼
/// 登录页的 `next`，包一层 Suspense。
export function ReauthProvider({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <Suspense fallback={null}>
        <ReauthDialog />
      </Suspense>
    </>
  );
}
