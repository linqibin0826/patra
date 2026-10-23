import { z } from "zod";
import { graphemeCount } from "@/lib/grapheme";
import { OCCUPATIONS, type Occupation } from "@/lib/identity/occupation";
import type { MyProfile } from "@/lib/identity/profile-schema";

/// 机构选择器的值：从 ROR 选的（`detail` 是卡片副行：英文名 · 城市 · 国家，回显已保存的机构时为 null），
/// 或手填的名称
export type OrganizationValue =
  | { kind: "ror"; rorId: string; name: string; detail: string | null }
  | { kind: "free"; name: string };

/// 个人资料表单的值（头像不在表单里，即时生效）
export interface ProfileFormValues {
  displayName: string;
  bio: string;
  links: { title: string; url: string }[];
  occupation: Occupation | "";
  jobTitle: string;
  organization: OrganizationValue | null;
  department: string;
  researchInterests: { ui: string; name: string }[];
}

/// `PUT /me/profile` 的请求体（PAP-95）：整份替换，空即清空
export interface UpdateProfileRequest {
  displayName: string | null;
  bio: string | null;
  links: { title: string | null; url: string }[];
  occupation: Occupation | null;
  jobTitle: string | null;
  organization: { rorId: string } | { name: string } | null;
  department: string | null;
  researchInterests: string[];
}

/// GET 的原值 → 表单值
export function toFormValues(profile: MyProfile): ProfileFormValues {
  const { organization } = profile;
  return {
    displayName: profile.displayName ?? "",
    bio: profile.bio ?? "",
    links: profile.links.map((link) => ({ title: link.title ?? "", url: link.url })),
    occupation: profile.occupation ?? "",
    jobTitle: profile.jobTitle ?? "",
    organization:
      organization === null
        ? null
        : organization.rorId === null
          ? { kind: "free", name: organization.name }
          : { kind: "ror", rorId: organization.rorId, name: organization.name, detail: null },
    department: profile.department ?? "",
    researchInterests: profile.researchInterests,
  };
}

/// 去首尾空白后为空就是 null
const orNull = (value: string): string | null => (value.trim() === "" ? null : value.trim());

/// 表单值 → PUT 请求体：空串变 null；ROR 机构只发 `rorId`（名称由 identity 向 catalog 取），手填只发
/// `name`；关注领域只发 UI。简介保留中间的换行。
export function toUpdateRequest(values: ProfileFormValues): UpdateProfileRequest {
  const { organization } = values;
  return {
    displayName: orNull(values.displayName),
    bio: values.bio.trim() === "" ? null : values.bio.trim(),
    links: values.links.map((link) => ({ title: orNull(link.title), url: link.url.trim() })),
    occupation: values.occupation === "" ? null : values.occupation,
    jobTitle: orNull(values.jobTitle),
    organization:
      organization === null
        ? null
        : organization.kind === "ror"
          ? { rorId: organization.rorId }
          : { name: organization.name.trim() },
    department: orNull(values.department),
    researchInterests: values.researchInterests.map((interest) => interest.ui),
  };
}

/// 字段错误文案（简报 §4.5）。键是把下标换成 `[]` 之后的后端字段路径，值按原因码取。
const FIELD_MESSAGES: Record<string, Record<string, string>> = {
  displayName: {
    TOO_SHORT: "昵称至少 2 个字",
    TOO_LONG: "昵称最多 30 个字",
    INVALID_CHARACTER: "昵称里有不支持的字符（如换行、零宽字符）",
    INVALID_FORMAT: "昵称不能是网址、邮箱、手机号或微信号",
    RESERVED: "这个昵称不能使用，请换一个",
  },
  bio: {
    TOO_LONG: "简介最多 200 字",
    TOO_MANY_LINES: "简介最多 3 行",
    INVALID_CHARACTER: "简介里有不支持的字符",
  },
  links: { TOO_MANY: "最多 5 条链接" },
  "links[].url": {
    REQUIRED: "请输入网址，或删除这一行",
    TOO_LONG: "网址最长 300 个字符",
    INVALID_FORMAT: "请输入以 http:// 或 https:// 开头的完整网址",
    DUPLICATE: "这条网址和上面重复了",
  },
  "links[].title": { TOO_LONG: "标题最多 30 个字", INVALID_CHARACTER: "标题里有不支持的字符" },
  occupation: { INVALID_FORMAT: "请重新选择身份" },
  jobTitle: { TOO_LONG: "职称最多 50 个字" },
  "organization.name": { TOO_LONG: "机构名称最多 200 个字" },
  "organization.rorId": {
    NOT_FOUND: "这个机构已不在机构库中，请重新选择或手动填写",
    INVALID_FORMAT: "这个机构已不在机构库中，请重新选择或手动填写",
  },
  department: { TOO_LONG: "科室最多 100 个字" },
  researchInterests: { TOO_MANY: "最多 10 个" },
  "researchInterests[]": {
    NOT_FOUND: "这个主题词已不可用，请移除",
    INVALID_FORMAT: "这个主题词已不可用，请移除",
    DUPLICATE: "重复了",
  },
};

/// 后端字段错误 → 文案；不认识的字段或原因码是「这一项不符合要求」
export function profileFieldMessage(field: string, code: string | null): string {
  const messages = FIELD_MESSAGES[field.replace(/\[\d+\]/g, "[]")];
  return (
    (code !== null && messages && Object.hasOwn(messages, code) ? messages[code] : undefined) ??
    "这一项不符合要求"
  );
}

/// 表单里认识的顶层字段
const FORM_FIELDS = new Set([
  "displayName",
  "bio",
  "links",
  "occupation",
  "jobTitle",
  "department",
  "researchInterests",
]);

/// 后端字段路径 → RHF 的字段路径：`links[1].url` → `links.1.url`，`researchInterests[3]` →
/// `researchInterests.3`，机构的两个子字段都落到选择器 `organization` 上；不认识的为 null。
export function formPathOf(field: string): string | null {
  if (field.startsWith("organization")) {
    return "organization";
  }
  const path = field.replace(/\[(\d+)\]/g, ".$1");
  return FORM_FIELDS.has(path.split(".")[0] ?? "") ? path : null;
}

/// 网址是不是 http(s) 开头、有主机、没有空白
function isWebUrl(value: string): boolean {
  if (/\s/.test(value) || !/^https?:\/\//i.test(value)) {
    return false;
  }
  try {
    return new URL(value).hostname !== "";
  } catch {
    return false;
  }
}

/// 文字字段的字素上限（release spec 2.1.2，与后端一致）
export const TEXT_LIMITS = {
  displayName: 30,
  bio: 200,
  linkTitle: 30,
  jobTitle: 50,
  organizationName: 200,
  department: 100,
} as const;

/// 后端对文字字段还卡 UTF-8 字节数：字素上限 × 4（昵称 120 字节）。表情组合序列一个字素就能占十几字节，
/// 字数没超也可能超这个
const BYTES_PER_GRAPHEME = 4;

/// UTF-8 字节数（去首尾空白后）
function utf8Length(value: string): number {
  return new TextEncoder().encode(value.trim()).length;
}

/// 字数没超、字节超了时的提示（后端同样回 TOO_LONG，但写「最多 N 个字」会和计数对不上）
const TOO_MANY_BYTES_MESSAGE = "表情符号占用的位置较多，请删减一些";

/// 文字字段的上限检查（去首尾空白后）：字素数超过 `max` 是 `TOO_LONG`；字素没超、UTF-8 字节超过
/// `max × 4` 是 `TOO_MANY_BYTES`；都没超为 null。前端校验、输入时的即时提示、计数的颜色都用它，规则只有这一份。
export function lengthIssue(value: string, max: number): "TOO_LONG" | "TOO_MANY_BYTES" | null {
  if (graphemeCount(value.trim()) > max) {
    return "TOO_LONG";
  }
  return utf8Length(value) > max * BYTES_PER_GRAPHEME ? "TOO_MANY_BYTES" : null;
}

/// `lengthIssue` 的文案：`TOO_LONG` 取该字段的「最多 N 个字」（`field` 是 `FIELD_MESSAGES` 的键），字节超了是
/// 删减表情的提示；没超为 undefined
export function lengthMessage(field: string, value: string, max: number): string | undefined {
  const issue = lengthIssue(value, max);
  if (issue === null) {
    return undefined;
  }
  return issue === "TOO_LONG" ? profileFieldMessage(field, "TOO_LONG") : TOO_MANY_BYTES_MESSAGE;
}

/// 有上限的文字字段：超了按 `lengthMessage` 报错
const limitedText = (field: string, max: number) =>
  z.string().superRefine((value, ctx) => {
    const message = lengthMessage(field, value, max);
    if (message) ctx.addIssue({ code: "custom", message });
  });

/// 前端校验（与后端同规则的那部分：长度、行数、条数、网址格式与重复）；字符、保留字、网址 / 手机号形态等
/// 只由后端判，失败时按原因码显示。文案取 `FIELD_MESSAGES`，两边一致。
export const profileFormSchema = z
  .object({
    displayName: limitedText("displayName", TEXT_LIMITS.displayName).refine(
      (value) => graphemeCount(value.trim()) !== 1,
      profileFieldMessage("displayName", "TOO_SHORT"),
    ),
    bio: limitedText("bio", TEXT_LIMITS.bio).refine(
      (value) => value.trim().split("\n").length <= 3,
      profileFieldMessage("bio", "TOO_MANY_LINES"),
    ),
    links: z
      .array(
        z.object({
          title: limitedText("links[].title", TEXT_LIMITS.linkTitle),
          url: z.string(),
        }),
      )
      .max(5, profileFieldMessage("links", "TOO_MANY")),
    occupation: z.union([z.enum(OCCUPATIONS), z.literal("")]),
    jobTitle: limitedText("jobTitle", TEXT_LIMITS.jobTitle),
    organization: z
      .union([
        z.object({
          kind: z.literal("ror"),
          rorId: z.string(),
          name: z.string(),
          detail: z.string().nullable(),
        }),
        z.object({ kind: z.literal("free"), name: z.string() }),
      ])
      .nullable(),
    department: limitedText("department", TEXT_LIMITS.department),
    researchInterests: z
      .array(z.object({ ui: z.string(), name: z.string() }))
      .max(10, profileFieldMessage("researchInterests", "TOO_MANY")),
  })
  .superRefine((values, ctx) => {
    // 机构的错误挂在选择器 `organization` 上，不挂在子字段上
    if (values.organization?.kind === "free") {
      const message = lengthMessage(
        "organization.name",
        values.organization.name,
        TEXT_LIMITS.organizationName,
      );
      if (message) {
        ctx.addIssue({ code: "custom", path: ["organization"], message });
      }
    }
    const seen = new Set<string>();
    values.links.forEach((link, index) => {
      const url = link.url.trim();
      const issue =
        url === ""
          ? "REQUIRED"
          : [...url].length > 300
            ? "TOO_LONG"
            : !isWebUrl(url)
              ? "INVALID_FORMAT"
              : seen.has(url.toLowerCase())
                ? "DUPLICATE"
                : null;
      seen.add(url.toLowerCase());
      if (issue) {
        ctx.addIssue({
          code: "custom",
          path: ["links", index, "url"],
          message: profileFieldMessage("links[].url", issue),
        });
      }
    });
  });
