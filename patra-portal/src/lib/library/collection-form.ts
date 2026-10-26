import { z } from "zod";
import { graphemeCount } from "@/lib/grapheme";
import { LIMITS, NAME_ERRORS } from "@/lib/library/messages";

/// 新建 / 编辑收藏夹的表单（简报 §3.11）：名称 1 ~ 100 字、简介 ≤ 500 字，都按字素计（与后端一致）；
/// 重名只在后端判
export const collectionFormSchema = z.object({
  name: z
    .string()
    .refine((value) => value.trim() !== "", NAME_ERRORS.required)
    .refine((value) => graphemeCount(value.trim()) <= LIMITS.name, NAME_ERRORS.tooLong),
  description: z
    .string()
    .refine((value) => graphemeCount(value.trim()) <= LIMITS.description, "简介最多 500 字"),
});

export type CollectionFormValues = z.infer<typeof collectionFormSchema>;
