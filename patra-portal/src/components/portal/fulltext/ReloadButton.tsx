"use client";

import { useRouter } from "next/navigation";
import { btnPrimary } from "@/lib/portal-ui";

/** 全文没能加载时重新取一次：刷新当前路由的服务端渲染，不整页重载。 */
export function ReloadButton() {
  const router = useRouter();
  return (
    <button type="button" className={btnPrimary} onClick={() => router.refresh()}>
      重新加载
    </button>
  );
}
