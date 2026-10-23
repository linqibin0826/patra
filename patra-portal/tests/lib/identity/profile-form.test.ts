import { EMPTY_PROFILE, MY_PROFILE } from "@tests/fixtures/profile";
import { describe, expect, it } from "vitest";
import {
  formPathOf,
  lengthMessage,
  type ProfileFormValues,
  profileFieldMessage,
  profileFormSchema,
  toFormValues,
  toUpdateRequest,
} from "@/lib/identity/profile-form";

const VALUES = toFormValues(MY_PROFILE);

/// 每个字段路径的第一条错误
function errorsOf(values: ProfileFormValues): Record<string, string> {
  const result = profileFormSchema.safeParse(values);
  const errors: Record<string, string> = {};
  for (const issue of result.success ? [] : result.error.issues) {
    errors[issue.path.join(".")] ??= issue.message;
  }
  return errors;
}

describe("表单值 ↔ 接口", () => {
  it("GET 的值转成表单值：null 变空串，ROR 机构带回显信息", () => {
    expect(VALUES).toEqual({
      displayName: "陈宇",
      bio: "心内科医生，关注心衰与房颤。\n业余写写科普。",
      links: [
        { title: "实验室主页", url: "https://lab.example.edu/chen" },
        { title: "", url: "https://scholar.example.com/chen" },
      ],
      occupation: "PHYSICIAN",
      jobTitle: "主治医师",
      organization: { kind: "ror", rorId: "04jztag35", name: "北京协和医院", detail: null },
      department: "心内科",
      researchInterests: MY_PROFILE.researchInterests,
    });
  });

  it("新用户：全部是空", () => {
    expect(toFormValues(EMPTY_PROFILE)).toMatchObject({
      displayName: "",
      bio: "",
      links: [],
      occupation: "",
      organization: null,
      researchInterests: [],
    });
  });

  it("手填机构转成 free", () => {
    expect(
      toFormValues({ ...EMPTY_PROFILE, organization: { rorId: null, name: "某某医院" } })
        .organization,
    ).toEqual({ kind: "free", name: "某某医院" });
  });

  it("表单值转成 PUT 请求体：ROR 只发 rorId，关注领域只发 UI", () => {
    expect(toUpdateRequest(VALUES)).toEqual({
      displayName: "陈宇",
      bio: "心内科医生，关注心衰与房颤。\n业余写写科普。",
      links: [
        { title: "实验室主页", url: "https://lab.example.edu/chen" },
        { title: null, url: "https://scholar.example.com/chen" },
      ],
      occupation: "PHYSICIAN",
      jobTitle: "主治医师",
      organization: { rorId: "04jztag35" },
      department: "心内科",
      researchInterests: ["D006333", "D001281"],
    });
  });

  it("空串变 null，手填机构只发 name", () => {
    const request = toUpdateRequest({
      ...toFormValues(EMPTY_PROFILE),
      displayName: "  ",
      organization: { kind: "free", name: "某某医院" },
    });
    expect(request).toMatchObject({
      displayName: null,
      bio: null,
      occupation: null,
      jobTitle: null,
      department: null,
      organization: { name: "某某医院" },
    });
  });
});

describe("profileFormSchema", () => {
  it("已填写的资料合规", () => {
    expect(errorsOf(VALUES)).toEqual({});
  });

  it.each([
    ["陈", "昵称至少 2 个字"],
    ["陈".repeat(31), "昵称最多 30 个字"],
  ])("昵称 %j：%s", (displayName, message) => {
    expect(errorsOf({ ...VALUES, displayName }).displayName).toBe(message);
  });

  it("字节也有上限（字素上限 × 4，与后端一致）：7 个家庭表情 126 字节超出昵称的 120 字节", () => {
    expect(errorsOf({ ...VALUES, displayName: "👨‍👩‍👧".repeat(7) }).displayName).toBe(
      "表情符号占用的位置较多，请删减一些",
    );
    expect(errorsOf({ ...VALUES, displayName: "👨‍👩‍👧".repeat(6) })).toEqual({});
    expect(errorsOf({ ...VALUES, jobTitle: "👨‍👩‍👧".repeat(12) }).jobTitle).toBe(
      "表情符号占用的位置较多，请删减一些",
    );
  });

  it("昵称空着可以（跟随用户名）", () => {
    expect(errorsOf({ ...VALUES, displayName: "" })).toEqual({});
  });

  it("简介按字素计 200，最多 3 行", () => {
    expect(errorsOf({ ...VALUES, bio: "字".repeat(201) }).bio).toBe("简介最多 200 字");
    expect(errorsOf({ ...VALUES, bio: "a\nb\nc\nd" }).bio).toBe("简介最多 3 行");
    expect(errorsOf({ ...VALUES, bio: "👨‍👩‍👧".repeat(44) })).toEqual({});
  });

  it("链接：网址必填、只收 http(s)、不能重复；标题最多 30 字；最多 5 条", () => {
    const link = (url: string, title = "") => ({ title, url });
    expect(errorsOf({ ...VALUES, links: [link("")] })["links.0.url"]).toBe(
      "请输入网址，或删除这一行",
    );
    expect(errorsOf({ ...VALUES, links: [link("ftp://x.org")] })["links.0.url"]).toBe(
      "请输入以 http:// 或 https:// 开头的完整网址",
    );
    expect(
      errorsOf({ ...VALUES, links: [link("https://a.org/x"), link("HTTPS://A.org/x")] })[
        "links.1.url"
      ],
    ).toBe("这条网址和上面重复了");
    expect(
      errorsOf({ ...VALUES, links: [link("https://a.org", "标".repeat(31))] })["links.0.title"],
    ).toBe("标题最多 30 个字");
    expect(
      errorsOf({
        ...VALUES,
        links: Array.from({ length: 6 }, (_, i) => link(`https://a.org/${i}`)),
      }).links,
    ).toBe("最多 5 条链接");
  });

  it("职称 50、科室 100、手填机构 200、关注领域 10 个", () => {
    expect(errorsOf({ ...VALUES, jobTitle: "职".repeat(51) }).jobTitle).toBe("职称最多 50 个字");
    expect(errorsOf({ ...VALUES, department: "科".repeat(101) }).department).toBe(
      "科室最多 100 个字",
    );
    expect(
      errorsOf({ ...VALUES, organization: { kind: "free", name: "院".repeat(201) } }).organization,
    ).toBe("机构名称最多 200 个字");
    expect(
      errorsOf({
        ...VALUES,
        researchInterests: Array.from({ length: 11 }, (_, i) => ({
          ui: `D00000${i}`,
          name: `T${i}`,
        })),
      }).researchInterests,
    ).toBe("最多 10 个");
  });
});

describe("后端字段路径 → 表单路径", () => {
  it.each([
    ["links[1].url", "links.1.url"],
    ["links[0].title", "links.0.title"],
    ["links", "links"],
    ["organization.rorId", "organization"],
    ["organization.name", "organization"],
    ["researchInterests[3]", "researchInterests.3"],
    ["researchInterests", "researchInterests"],
    ["displayName", "displayName"],
    ["bio", "bio"],
    ["occupation", "occupation"],
    ["unknown", null],
  ])("%s → %s", (field, path) => {
    expect(formPathOf(field)).toBe(path);
  });
});

describe("profileFieldMessage", () => {
  it.each([
    ["displayName", "INVALID_FORMAT", "昵称不能是网址、邮箱、手机号或微信号"],
    ["displayName", "RESERVED", "这个昵称不能使用，请换一个"],
    ["displayName", "INVALID_CHARACTER", "昵称里有不支持的字符（如换行、零宽字符）"],
    ["bio", "INVALID_CHARACTER", "简介里有不支持的字符"],
    ["links[2].url", "DUPLICATE", "这条网址和上面重复了"],
    ["links[0].title", "INVALID_CHARACTER", "标题里有不支持的字符"],
    ["occupation", "INVALID_FORMAT", "请重新选择身份"],
    ["organization.rorId", "NOT_FOUND", "这个机构已不在机构库中，请重新选择或手动填写"],
    ["organization.rorId", "INVALID_FORMAT", "这个机构已不在机构库中，请重新选择或手动填写"],
    ["researchInterests[1]", "NOT_FOUND", "这个主题词已不可用，请移除"],
    ["researchInterests[1]", "DUPLICATE", "重复了"],
    ["jobTitle", "INVALID_CHARACTER", "这一项不符合要求"],
    ["bio", null, "这一项不符合要求"],
  ])("%s %s → %s", (field, code, message) => {
    expect(profileFieldMessage(field, code)).toBe(message);
  });
});

describe("lengthMessage", () => {
  it.each([
    ["没超", "displayName", "陈宇", 30, undefined],
    ["字数超了", "displayName", "字".repeat(31), 30, "昵称最多 30 个字"],
    ["字数没超、字节超了", "bio", "👨‍👩‍👧‍👦".repeat(40), 200, "表情符号占用的位置较多，请删减一些"],
    ["首尾空白不算", "displayName", `  ${"字".repeat(30)}  `, 30, undefined],
    ["链接标题按 links[].title 取文案", "links[].title", "字".repeat(31), 30, "标题最多 30 个字"],
  ] as const)("%s", (_, field, value, max, message) => {
    expect(lengthMessage(field, value, max)).toBe(message);
  });
});
