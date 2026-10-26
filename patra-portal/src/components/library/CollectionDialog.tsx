"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { CharCounter } from "@/components/settings/profile/CharCounter";
import { Dialog } from "@/components/ui/dialog";
import { createCollection, updateCollection } from "@/lib/interaction/collection-actions";
import type { Collection } from "@/lib/interaction/schema";
import { invalidateLibrary } from "@/lib/library/cache";
import { type CollectionFormValues, collectionFormSchema } from "@/lib/library/collection-form";
import { LIMITS, NAME_ERRORS } from "@/lib/library/messages";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

type CollectionDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
} & ({ mode: "create"; collection?: undefined } | { mode: "edit"; collection: Collection });

/// 新建 / 编辑收藏夹对话框（简报 §3.11）：名称（必填）、简介（可选），字素计数。不乐观：提交中禁用；
/// 字段错误与重名在字段下方，其余失败在上方提示条。新建成功进入新收藏夹，编辑成功关闭；都出 toast
export function CollectionDialog({ open, onOpenChange, mode, collection }: CollectionDialogProps) {
  const client = useQueryClient();
  const router = useRouter();
  const redirectToLogin = useLoginRedirect();
  const [failure, setFailure] = useState(false);
  const form = useForm<CollectionFormValues>({
    resolver: zodResolver(collectionFormSchema),
    defaultValues: { name: collection?.name ?? "", description: collection?.description ?? "" },
  });
  const { register, handleSubmit, watch, setError, formState, reset } = form;
  const saving = formState.isSubmitting;

  // 打开时（或换了收藏夹时）带出原值；概览重取换了对象引用但还是同一个夹时不动，免得清掉正在输入的内容
  const collectionId = collection?.collectionId;
  const initial = useRef({
    name: collection?.name ?? "",
    description: collection?.description ?? "",
  });
  initial.current = { name: collection?.name ?? "", description: collection?.description ?? "" };
  // biome-ignore lint/correctness/useExhaustiveDependencies: 只在打开、换了收藏夹时带出原值（见上），对象引用变了不算
  useEffect(() => {
    if (open) {
      reset(initial.current);
      setFailure(false);
    }
  }, [open, collectionId, reset]);

  const submit = handleSubmit(async (values) => {
    setFailure(false);
    const input = { name: values.name.trim(), description: values.description };
    const result = await orUnavailable(
      mode === "create"
        ? createCollection(input)
        : updateCollection(collection.collectionId, input),
    );
    if (result.ok) {
      void invalidateLibrary(client);
      onOpenChange(false);
      if (mode === "create") {
        router.push(`/library/${result.collection.collectionId}`);
        toast.success(`已创建收藏夹「${result.collection.name}」`);
      } else {
        toast.success("收藏夹已保存");
      }
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    if (result.reason === "name-taken") {
      setError("name", { message: NAME_ERRORS.taken });
      return;
    }
    if (result.reason === "invalid-fields") {
      for (const field of result.fieldErrors) {
        if (field.field === "name" || field.field === "description") {
          setError(field.field, {
            message:
              field.field === "name"
                ? field.code === "REQUIRED"
                  ? NAME_ERRORS.required
                  : NAME_ERRORS.tooLong
                : "简介最多 500 字",
          });
        }
      }
      return;
    }
    setFailure(true);
  });

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      dismissible={!saving}
      title={mode === "create" ? "新建收藏夹" : "编辑收藏夹"}
      size="sm"
      alert={failure ? <FormAlert>没有保存成功，请稍后再试</FormAlert> : null}
    >
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <FormField
          id="collection-name"
          label="名称"
          aside={<CharCounter value={watch("name")} max={LIMITS.name} />}
          error={formState.errors.name?.message}
        >
          {(control) => (
            <input {...control} {...register("name")} readOnly={saving} autoComplete="off" />
          )}
        </FormField>
        <FormField
          id="collection-description"
          label="简介"
          optional
          aside={<CharCounter value={watch("description")} max={LIMITS.description} />}
          error={formState.errors.description?.message}
          hint={mode === "create" ? "新建的收藏夹只有你能看到，之后可以设为公开。" : undefined}
        >
          {(control) => (
            <textarea {...control} {...register("description")} rows={3} readOnly={saving} />
          )}
        </FormField>
        <div className="flex justify-end gap-4">
          <button
            type="button"
            className={cn(btnText, "text-sm")}
            onClick={() => !saving && onOpenChange(false)}
          >
            取消
          </button>
          <button type="submit" aria-disabled={saving || undefined} className={btnPrimary}>
            {mode === "create" ? (saving ? "创建中…" : "创建") : saving ? "保存中…" : "保存"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
