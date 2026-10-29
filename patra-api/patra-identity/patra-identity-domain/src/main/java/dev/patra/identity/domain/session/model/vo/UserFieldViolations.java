package dev.patra.identity.domain.session.model.vo;

import dev.patra.tool.core.error.field.FieldViolation;

/// 前台用户注册、登录、改密码、找回密码、改邮箱时会报的字段错误。前端按原因码选文案，`message` 只是默认文案。
public final class UserFieldViolations {

  /// 邮箱字段名，和请求体一致。
  public static final String EMAIL = "email";

  /// 密码字段名，和请求体一致。
  public static final String PASSWORD = "password";

  /// 客户端类型字段名，和请求体一致。
  public static final String CLIENT_TYPE = "clientType";

  /// 设备标识字段名，和请求体一致。
  public static final String DEVICE_ID = "deviceId";

  /// 为空。
  public static final String REQUIRED = "REQUIRED";

  /// 太长。
  public static final String TOO_LONG = "TOO_LONG";

  /// 格式不对。
  public static final String INVALID_FORMAT = "INVALID_FORMAT";

  /// 太短。
  public static final String TOO_SHORT = "TOO_SHORT";

  /// 在常见密码名单里。
  public static final String TOO_COMMON = "TOO_COMMON";

  /// 含不合法的 Unicode（孤立的代理项）或不允许的字符。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  /// 用户名字段名，和请求体一致。
  public static final String USERNAME = "username";

  /// 昵称字段名，和请求体一致。
  public static final String DISPLAY_NAME = "displayName";

  /// 全是数字。
  public static final String DIGITS_ONLY = "DIGITS_ONLY";

  /// 保留字或拒绝词。
  public static final String RESERVED = "RESERVED";

  /// 和当前值相同。
  public static final String UNCHANGED = "UNCHANGED";

  /// 已被别人使用。
  public static final String ALREADY_TAKEN = "ALREADY_TAKEN";

  /// 已注册。
  public static final String ALREADY_REGISTERED = "ALREADY_REGISTERED";

  /// 批量用户摘要的用户 ID 列表字段名，和请求体一致（PAP-108）。
  public static final String USER_IDS = "userIds";

  /// @ 联想的用户名前缀字段名，和请求体一致（PAP-118）。
  public static final String PREFIX = "prefix";

  /// @ 联想的条数上限字段名，和请求体一致（PAP-118）。
  public static final String LIMIT = "limit";

  /// 用户名批量解析的用户名列表字段名，和请求体一致（PAP-118）。
  public static final String USERNAMES = "usernames";

  /// 一次性令牌字段名，和请求体一致。
  public static final String TOKEN = "token";

  /// 令牌不存在、已过期、已使用或已作废，不区分原因。
  public static final String INVALID = "INVALID";

  /// 当前密码字段名（改密码），和请求体一致。
  public static final String CURRENT_PASSWORD = "currentPassword";

  /// 新密码字段名（改密码、重设密码），和请求体一致。
  public static final String NEW_PASSWORD = "newPassword";

  /// 新邮箱字段名（改邮箱），和请求体一致。
  public static final String NEW_EMAIL = "newEmail";

  /// 简介字段名（资料），和请求体一致。
  public static final String BIO = "bio";

  /// 链接列表字段名（资料），和请求体一致。
  public static final String LINKS = "links";

  /// 身份大类字段名（资料），和请求体一致。
  public static final String OCCUPATION = "occupation";

  /// 职称字段名（资料），和请求体一致。
  public static final String JOB_TITLE = "jobTitle";

  /// 机构 ROR ID 字段路径（资料），和请求体一致。
  public static final String ORGANIZATION_ROR_ID = "organization.rorId";

  /// 机构名字段路径（资料），和请求体一致。
  public static final String ORGANIZATION_NAME = "organization.name";

  /// 科室字段名（资料），和请求体一致。
  public static final String DEPARTMENT = "department";

  /// 关注领域列表字段名（资料），和请求体一致。
  public static final String RESEARCH_INTERESTS = "researchInterests";

  /// 主页可见性字段名（隐私），和请求体一致。
  public static final String PROFILE_VISIBILITY = "profileVisibility";

  /// 专业背景可见性字段名（隐私），和请求体一致。
  public static final String PROFESSIONAL_VISIBILITY = "professionalVisibility";

  /// 搜索引擎收录字段名（隐私），和请求体一致。
  public static final String SEARCH_INDEXING = "searchIndexing";

  /// 条目太多。
  public static final String TOO_MANY = "TOO_MANY";

  /// 行数太多。
  public static final String TOO_MANY_LINES = "TOO_MANY_LINES";

  /// 和前面的某一项重复。
  public static final String DUPLICATE = "DUPLICATE";

  /// catalog 里没有这个标识。
  public static final String NOT_FOUND = "NOT_FOUND";

  /// 原因码：输入与应有的值不一致（注销时确认的用户名）。
  public static final String MISMATCH = "MISMATCH";

  /// 只有静态方法。
  private UserFieldViolations() {}

  /// 邮箱为空。
  ///
  /// @return 字段错误
  public static FieldViolation emailRequired() {
    return emailRequired(EMAIL);
  }

  /// 邮箱为空，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation emailRequired(String field) {
    return FieldViolation.of(field, REQUIRED, "请输入邮箱");
  }

  /// 邮箱太长。
  ///
  /// @return 字段错误
  public static FieldViolation emailTooLong() {
    return emailTooLong(EMAIL);
  }

  /// 邮箱太长，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation emailTooLong(String field) {
    return FieldViolation.of(field, TOO_LONG, "邮箱最长 254 个字符");
  }

  /// 邮箱格式不对。
  ///
  /// @return 字段错误
  public static FieldViolation emailInvalidFormat() {
    return emailInvalidFormat(EMAIL);
  }

  /// 邮箱格式不对，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation emailInvalidFormat(String field) {
    return FieldViolation.of(field, INVALID_FORMAT, "邮箱格式不正确");
  }

  /// 密码为空。
  ///
  /// @return 字段错误
  public static FieldViolation passwordRequired() {
    return passwordRequired(PASSWORD);
  }

  /// 密码为空，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation passwordRequired(String field) {
    return FieldViolation.of(field, REQUIRED, "请输入密码");
  }

  /// 密码含不合法的 Unicode。
  ///
  /// @return 字段错误
  public static FieldViolation passwordInvalidCharacter() {
    return passwordInvalidCharacter(PASSWORD);
  }

  /// 密码含不合法的 Unicode，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation passwordInvalidCharacter(String field) {
    return FieldViolation.of(field, INVALID_CHARACTER, "密码包含无效字符");
  }

  /// 密码太短。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooShort() {
    return passwordTooShort(PASSWORD);
  }

  /// 密码太短，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation passwordTooShort(String field) {
    return FieldViolation.of(field, TOO_SHORT, "密码至少 8 位");
  }

  /// 密码太长。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooLong() {
    return passwordTooLong(PASSWORD);
  }

  /// 密码太长，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation passwordTooLong(String field) {
    return FieldViolation.of(field, TOO_LONG, "密码最长 64 位");
  }

  /// 密码太常见。
  ///
  /// @return 字段错误
  public static FieldViolation passwordTooCommon() {
    return passwordTooCommon(PASSWORD);
  }

  /// 密码太常见，按指定的字段名报错。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation passwordTooCommon(String field) {
    return FieldViolation.of(field, TOO_COMMON, "这个密码太常见，容易被猜到，请换一个");
  }

  /// 客户端类型不认识。
  ///
  /// @return 字段错误
  public static FieldViolation clientTypeInvalidFormat() {
    return FieldViolation.of(CLIENT_TYPE, INVALID_FORMAT, "不支持的客户端类型");
  }

  /// 设备标识格式不对（不是 43 个 base64url 字符）。
  ///
  /// @return 字段错误
  public static FieldViolation deviceIdInvalidFormat() {
    return FieldViolation.of(DEVICE_ID, INVALID_FORMAT, "设备标识必须是 43 个 base64url 字符");
  }

  /// 邮箱已注册。
  ///
  /// @return 字段错误
  public static FieldViolation emailAlreadyRegistered() {
    return FieldViolation.of(EMAIL, ALREADY_REGISTERED, "该邮箱已注册");
  }

  /// 用户名为空。
  ///
  /// @return 字段错误
  public static FieldViolation usernameRequired() {
    return FieldViolation.of(USERNAME, REQUIRED, "请输入用户名");
  }

  /// 用户名含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation usernameInvalidCharacter() {
    return FieldViolation.of(USERNAME, INVALID_CHARACTER, "用户名只能包含小写字母、数字、_ 和 -");
  }

  /// 用户名太短。
  ///
  /// @return 字段错误
  public static FieldViolation usernameTooShort() {
    return FieldViolation.of(USERNAME, TOO_SHORT, "用户名至少 3 个字符");
  }

  /// 用户名太长。
  ///
  /// @return 字段错误
  public static FieldViolation usernameTooLong() {
    return FieldViolation.of(USERNAME, TOO_LONG, "用户名最长 30 个字符");
  }

  /// 用户名首尾不是字母数字，或 `_`、`-` 连续出现。
  ///
  /// @return 字段错误
  public static FieldViolation usernameInvalidFormat() {
    return FieldViolation.of(USERNAME, INVALID_FORMAT, "用户名首尾须是字母或数字，_ 和 - 不能连续");
  }

  /// 用户名全是数字。
  ///
  /// @return 字段错误
  public static FieldViolation usernameDigitsOnly() {
    return FieldViolation.of(USERNAME, DIGITS_ONLY, "用户名不能全是数字");
  }

  /// 用户名命中保留字。
  ///
  /// @return 字段错误
  public static FieldViolation usernameReserved() {
    return FieldViolation.of(USERNAME, RESERVED, "这个用户名不能使用");
  }

  /// 注销时输入的用户名与当前用户名不一致。
  ///
  /// @return 字段错误
  public static FieldViolation usernameMismatch() {
    return FieldViolation.of(USERNAME, MISMATCH, "和你的用户名不一致");
  }

  /// 新用户名与当前相同。
  ///
  /// @return 字段错误
  public static FieldViolation usernameUnchanged() {
    return FieldViolation.of(USERNAME, UNCHANGED, "新用户名与当前相同");
  }

  /// 令牌为空。
  ///
  /// @return 字段错误
  public static FieldViolation tokenRequired() {
    return FieldViolation.of(TOKEN, REQUIRED, "请提供验证令牌");
  }

  /// 令牌不可用：格式不对、不存在、已过期、已使用或已作废。
  ///
  /// @return 字段错误
  public static FieldViolation tokenInvalid() {
    return FieldViolation.of(TOKEN, INVALID, "验证链接无效或已过期");
  }

  /// 用户名已被使用。
  ///
  /// @return 字段错误
  public static FieldViolation usernameAlreadyTaken() {
    return FieldViolation.of(USERNAME, ALREADY_TAKEN, "该用户名已被使用");
  }

  /// 昵称为空。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameRequired() {
    return FieldViolation.of(DISPLAY_NAME, REQUIRED, "请输入昵称");
  }

  /// 昵称含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameInvalidCharacter() {
    return FieldViolation.of(DISPLAY_NAME, INVALID_CHARACTER, "昵称包含不允许的字符");
  }

  /// 昵称太短。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameTooShort() {
    return FieldViolation.of(DISPLAY_NAME, TOO_SHORT, "昵称至少 2 个字");
  }

  /// 昵称太长。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameTooLong() {
    return FieldViolation.of(DISPLAY_NAME, TOO_LONG, "昵称最长 30 个字");
  }

  /// 昵称像网址、邮箱、手机号或微信号。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameInvalidFormat() {
    return FieldViolation.of(DISPLAY_NAME, INVALID_FORMAT, "昵称不能是网址、邮箱、手机号或微信号");
  }

  /// 昵称命中拒绝词。
  ///
  /// @return 字段错误
  public static FieldViolation displayNameReserved() {
    return FieldViolation.of(DISPLAY_NAME, RESERVED, "这个昵称不能使用");
  }

  /// 新密码与当前密码相同。
  ///
  /// @return 字段错误
  public static FieldViolation newPasswordUnchanged() {
    return FieldViolation.of(NEW_PASSWORD, UNCHANGED, "新密码不能与当前密码相同");
  }

  /// 新邮箱与当前邮箱相同。
  ///
  /// @return 字段错误
  public static FieldViolation newEmailUnchanged() {
    return FieldViolation.of(NEW_EMAIL, UNCHANGED, "新邮箱与当前邮箱相同");
  }

  /// 简介含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation bioInvalidCharacter() {
    return FieldViolation.of(BIO, INVALID_CHARACTER, "简介包含不允许的字符");
  }

  /// 简介太长。
  ///
  /// @return 字段错误
  public static FieldViolation bioTooLong() {
    return FieldViolation.of(BIO, TOO_LONG, "简介最长 200 个字");
  }

  /// 简介超过 3 行。
  ///
  /// @return 字段错误
  public static FieldViolation bioTooManyLines() {
    return FieldViolation.of(BIO, TOO_MANY_LINES, "简介最多 3 行");
  }

  /// 链接超过 5 条。
  ///
  /// @return 字段错误
  public static FieldViolation linksTooMany() {
    return FieldViolation.of(LINKS, TOO_MANY, "链接最多 5 条");
  }

  /// 链接的 URL 为空。
  ///
  /// @param index 链接下标
  /// @return 字段错误
  public static FieldViolation linkUrlRequired(int index) {
    return FieldViolation.of(linkField(index, "url"), REQUIRED, "请输入链接地址");
  }

  /// 链接的 URL 太长。
  ///
  /// @param index 链接下标
  /// @return 字段错误
  public static FieldViolation linkUrlTooLong(int index) {
    return FieldViolation.of(linkField(index, "url"), TOO_LONG, "链接地址最长 300 个字符");
  }

  /// 链接的 URL 不合法：解析失败、不是 http(s)、没有主机名、带用户信息、含空白。
  ///
  /// @param index 链接下标
  /// @return 字段错误
  public static FieldViolation linkUrlInvalidFormat(int index) {
    return FieldViolation.of(
        linkField(index, "url"), INVALID_FORMAT, "链接地址只支持 http:// 或 https:// 开头的网址");
  }

  /// 链接的 URL 与前面某条重复。
  ///
  /// @param index 链接下标
  /// @return 字段错误
  public static FieldViolation linkUrlDuplicate(int index) {
    return FieldViolation.of(linkField(index, "url"), DUPLICATE, "这条链接已经添加过了");
  }

  /// 链接标题含不允许的字符。
  ///
  /// @param index 链接下标
  /// @return 字段错误
  public static FieldViolation linkTitleInvalidCharacter(int index) {
    return FieldViolation.of(linkField(index, "title"), INVALID_CHARACTER, "链接标题包含不允许的字符");
  }

  /// 链接标题太长。
  ///
  /// @param index 链接下标
  /// @return 字段错误
  public static FieldViolation linkTitleTooLong(int index) {
    return FieldViolation.of(linkField(index, "title"), TOO_LONG, "链接标题最长 30 个字");
  }

  /// 身份大类不认识。
  ///
  /// @return 字段错误
  public static FieldViolation occupationInvalidFormat() {
    return FieldViolation.of(OCCUPATION, INVALID_FORMAT, "不支持的身份类别");
  }

  /// 职称含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation jobTitleInvalidCharacter() {
    return FieldViolation.of(JOB_TITLE, INVALID_CHARACTER, "职称包含不允许的字符");
  }

  /// 职称太长。
  ///
  /// @return 字段错误
  public static FieldViolation jobTitleTooLong() {
    return FieldViolation.of(JOB_TITLE, TOO_LONG, "职称最长 50 个字");
  }

  /// 科室含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation departmentInvalidCharacter() {
    return FieldViolation.of(DEPARTMENT, INVALID_CHARACTER, "科室包含不允许的字符");
  }

  /// 科室太长。
  ///
  /// @return 字段错误
  public static FieldViolation departmentTooLong() {
    return FieldViolation.of(DEPARTMENT, TOO_LONG, "科室最长 100 个字");
  }

  /// 机构的 ROR ID 格式不对。
  ///
  /// @return 字段错误
  public static FieldViolation organizationRorIdInvalidFormat() {
    return FieldViolation.of(ORGANIZATION_ROR_ID, INVALID_FORMAT, "机构标识格式不正确");
  }

  /// catalog 里没有这个机构（或它已停用）。
  ///
  /// @return 字段错误
  public static FieldViolation organizationRorIdNotFound() {
    return FieldViolation.of(ORGANIZATION_ROR_ID, NOT_FOUND, "找不到这个机构，请重新选择");
  }

  /// 手填的机构名含不允许的字符。
  ///
  /// @return 字段错误
  public static FieldViolation organizationNameInvalidCharacter() {
    return FieldViolation.of(ORGANIZATION_NAME, INVALID_CHARACTER, "机构名包含不允许的字符");
  }

  /// 手填的机构名太长。
  ///
  /// @return 字段错误
  public static FieldViolation organizationNameTooLong() {
    return FieldViolation.of(ORGANIZATION_NAME, TOO_LONG, "机构名最长 200 个字");
  }

  /// 关注领域超过 10 个。
  ///
  /// @return 字段错误
  public static FieldViolation researchInterestsTooMany() {
    return FieldViolation.of(RESEARCH_INTERESTS, TOO_MANY, "关注领域最多 10 个");
  }

  /// 关注领域的主题词为空。
  ///
  /// @param index 下标
  /// @return 字段错误
  public static FieldViolation researchInterestRequired(int index) {
    return FieldViolation.of(interestField(index), REQUIRED, "请选择主题词");
  }

  /// 关注领域的主题词 UI 格式不对。
  ///
  /// @param index 下标
  /// @return 字段错误
  public static FieldViolation researchInterestInvalidFormat(int index) {
    return FieldViolation.of(interestField(index), INVALID_FORMAT, "主题词标识格式不正确");
  }

  /// 关注领域与前面某个重复。
  ///
  /// @param index 下标
  /// @return 字段错误
  public static FieldViolation researchInterestDuplicate(int index) {
    return FieldViolation.of(interestField(index), DUPLICATE, "这个主题词已经添加过了");
  }

  /// catalog 里没有这个主题词。
  ///
  /// @param index 下标
  /// @return 字段错误
  public static FieldViolation researchInterestNotFound(int index) {
    return FieldViolation.of(interestField(index), NOT_FOUND, "找不到这个主题词，请重新选择");
  }

  /// 隐私设置的某项为空。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation privacyRequired(String field) {
    return FieldViolation.of(field, REQUIRED, "请选择");
  }

  /// 隐私设置的某项取值不认识。
  ///
  /// @param field 字段名
  /// @return 字段错误
  public static FieldViolation privacyInvalidFormat(String field) {
    return FieldViolation.of(field, INVALID_FORMAT, "不支持的取值");
  }

  /// 批量用户摘要缺少用户 ID 列表。
  ///
  /// @return 字段错误
  public static FieldViolation userIdsRequired() {
    return FieldViolation.of(USER_IDS, REQUIRED, "缺少用户 ID 列表");
  }

  /// 批量用户摘要的用户 ID 列表里有空值。
  ///
  /// @return 字段错误
  public static FieldViolation userIdsInvalidFormat() {
    return FieldViolation.of(USER_IDS, INVALID_FORMAT, "用户 ID 不能为空");
  }

  /// 批量用户摘要一次超过上限。
  ///
  /// @return 字段错误
  public static FieldViolation userIdsTooMany() {
    return FieldViolation.of(USER_IDS, TOO_MANY, "一次最多 100 个用户 ID");
  }

  /// @ 联想缺少前缀。
  ///
  /// @return 字段错误
  public static FieldViolation prefixRequired() {
    return FieldViolation.of(PREFIX, REQUIRED, "缺少前缀");
  }

  /// @ 联想缺少条数上限。
  ///
  /// @return 字段错误
  public static FieldViolation limitRequired() {
    return FieldViolation.of(LIMIT, REQUIRED, "缺少条数上限");
  }

  /// @ 联想的条数上限越界。
  ///
  /// @return 字段错误
  public static FieldViolation limitOutOfRange() {
    return FieldViolation.of(LIMIT, INVALID_FORMAT, "条数上限是 1 ~ 20");
  }

  /// 用户名批量解析缺少用户名列表。
  ///
  /// @return 字段错误
  public static FieldViolation usernamesRequired() {
    return FieldViolation.of(USERNAMES, REQUIRED, "缺少用户名列表");
  }

  /// 用户名批量解析一次超过上限。
  ///
  /// @return 字段错误
  public static FieldViolation usernamesTooMany() {
    return FieldViolation.of(USERNAMES, TOO_MANY, "一次最多 100 个用户名");
  }

  /// 第 `index` 条链接的子字段路径，如 `links[1].url`。
  ///
  /// @param index 下标
  /// @param subField 子字段名
  /// @return 字段路径
  private static String linkField(int index, String subField) {
    return LINKS + "[" + index + "]." + subField;
  }

  /// 第 `index` 个关注领域的字段路径，如 `researchInterests[3]`。
  ///
  /// @param index 下标
  /// @return 字段路径
  private static String interestField(int index) {
    return RESEARCH_INTERESTS + "[" + index + "]";
  }
}
