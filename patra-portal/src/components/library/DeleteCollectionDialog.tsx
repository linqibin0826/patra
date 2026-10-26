"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { Dialog } from "@/components/ui/dialog";
import { deleteCollection } from "@/lib/interaction/collection-actions";
import type { Collection } from "@/lib/interaction/schema";
import { invalidateLibrary } from "@/lib/library/cache";
import { formatCount } from "@/lib/library/scope";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 删除收藏夹对话框（简报 §3.11）：条目留在文献库；公开的多一句「链接会失效」。成功后回 `/library`
export function DeleteCollectionDialog({
  collection,
  open,
  onOpenChange,
}: {
  collection: Collection;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const client = useQueryClient();
  const router = useRouter();
  const redirectToLogin = useLoginRedirect();
  const [deleting, setDeleting] = useState(false);
  const [failed, setFailed] = useState(false);

  async function confirm() {
    setDeleting(true);
    setFailed(false);
    const result = await orUnavailable(deleteCollection(collection.collectionId));
    setDeleting(false);
    if (result.ok) {
      onOpenChange(false);
      router.push("/library");
      toast.success(`已删除收藏夹「${collection.name}」`);
      void invalidateLibrary(client);
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    setFailed(true);
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      dismissible={!deleting}
      title={`删除收藏夹「${collection.name}」？`}
      size="sm"
      alert={failed ? <FormAlert>没有删除成功，请稍后再试</FormAlert> : null}
    >
      <div className="flex flex-col gap-2 text-sm leading-6 text-fg-2">
        <p>
          里面的 {formatCount(collection.itemCount)}{" "}
          篇文献不会从文献库删除；不在其他收藏夹里的，会回到「未分类」。
        </p>
        {collection.visibility === "PUBLIC" && <p>公开链接会失效，你的主页上也不再显示它。</p>}
      </div>
      <div className="flex justify-end gap-4">
        <button
          type="button"
          className={cn(btnText, "text-sm")}
          onClick={() => !deleting && onOpenChange(false)}
        >
          取消
        </button>
        <button
          type="button"
          aria-disabled={deleting || undefined}
          className={btnDanger}
          onClick={() => !deleting && void confirm()}
        >
          删除收藏夹
        </button>
      </div>
    </Dialog>
  );
}
