package dev.patra.interaction.domain.post.model.vo;

import dev.patra.interaction.domain.post.exception.InvalidExperienceException;
import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.policy.ExperienceRules;
import dev.patra.tool.core.error.field.FieldViolation;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/// 解析好的投稿经验：结构化字段与正文（可空）。
///
/// {@link #parse} 按字段顺序逐个解析，交叉规则（日期先后、首轮与最终结果的一致性，spec 4.2）只在参与的字段都合法时才判，
/// 有错一次抛出全部字段错误。
///
/// @param fields 结构化字段
/// @param body 正文，可空
public record ExperienceDraft(ExperienceFields fields, PostBody body) {

  /// 字段错误码：必填。
  public static final String REQUIRED = "REQUIRED";

  /// 字段错误码：日期写法不对或枚举值未知。
  public static final String INVALID = "INVALID";

  /// 字段错误码：晚于今天。
  public static final String FUTURE = "FUTURE";

  /// 字段错误码：早于最早日期。
  public static final String TOO_EARLY = "TOO_EARLY";

  /// 字段错误码：首轮决定日期早于投稿日期。
  public static final String BEFORE_SUBMISSION = "BEFORE_SUBMISSION";

  /// 字段错误码：最终决定日期早于首轮决定日期。
  public static final String BEFORE_FIRST_DECISION = "BEFORE_FIRST_DECISION";

  /// 字段错误码：这种组合下不该填。
  public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

  /// 字段错误码：与首轮结果矛盾。
  public static final String INCONSISTENT = "INCONSISTENT";

  /// 字段错误码：轮数、评分越界。
  public static final String OUT_OF_RANGE = "OUT_OF_RANGE";

  /// 创建。
  ///
  /// @param fields 结构化字段
  /// @param body 正文，可空
  /// @return 草稿
  public static ExperienceDraft of(ExperienceFields fields, PostBody body) {
    return new ExperienceDraft(fields, body);
  }

  /// 解析用户输入。
  ///
  /// @param input 原文
  /// @param today 北京时间的今天
  /// @return 草稿
  /// @throws InvalidExperienceException 有任何字段错误
  public static ExperienceDraft parse(ExperienceInput input, LocalDate today) {
    List<FieldViolation> violations = new ArrayList<>();
    LocalDate submittedOn = requiredDate(input.submittedOn(), "submittedOn", today, violations);
    LocalDate firstDecisionOn =
        requiredDate(input.firstDecisionOn(), "firstDecisionOn", today, violations);
    if (submittedOn != null && firstDecisionOn != null && firstDecisionOn.isBefore(submittedOn)) {
      violations.add(violation("firstDecisionOn", BEFORE_SUBMISSION, "首轮决定日期不能早于投稿日期"));
      firstDecisionOn = null;
    }
    FirstDecision firstDecision =
        requiredEnum(input.firstDecision(), "firstDecision", FirstDecision::valueOf, violations);
    FinalOutcome finalOutcome =
        requiredEnum(input.finalOutcome(), "finalOutcome", FinalOutcome::valueOf, violations);
    if (firstDecision != null
        && finalOutcome != null
        && firstDecision.impliedOutcome().filter(o -> o != finalOutcome).isPresent()) {
      violations.add(violation("finalOutcome", INCONSISTENT, "最终结果与首轮结果矛盾"));
    }
    LocalDate finalDecisionOn =
        finalDecisionOn(input, firstDecision, finalOutcome, firstDecisionOn, today, violations);
    ArticleType articleType =
        requiredEnum(input.articleType(), "articleType", ArticleType::valueOf, violations);
    Integer reviewRounds = reviewRounds(input.reviewRounds(), firstDecision, violations);
    Integer reviewQuality = reviewQuality(input.reviewQuality(), firstDecision, violations);
    Integer editorCommunication =
        rating(input.editorCommunication(), "editorCommunication", violations);
    PostBody body = PostBody.parse(input.body(), violations).orElse(null);
    if (!violations.isEmpty()) {
      throw new InvalidExperienceException(violations);
    }
    ExperienceFields fields =
        ExperienceFields.builder()
            .submittedOn(submittedOn)
            .firstDecisionOn(firstDecisionOn)
            .firstDecision(firstDecision)
            .finalOutcome(finalOutcome)
            .finalDecisionOn(finalDecisionOn)
            .articleType(articleType)
            .reviewRounds(reviewRounds)
            .reviewQuality(reviewQuality)
            .editorCommunication(editorCommunication)
            .apcPaid(input.apcPaid())
            .previouslyRejected(input.previouslyRejected())
            .build();
    return new ExperienceDraft(fields, body);
  }

  /// 最终决定日期：进行中或首轮终局时不该填；否则选填，不早于首轮决定日期。
  ///
  /// @param input 原文
  /// @param firstDecision 首轮结果，未解析成功为 `null`
  /// @param finalOutcome 最终结果，未解析成功为 `null`
  /// @param firstDecisionOn 首轮决定日期，不合法为 `null`
  /// @param today 今天
  /// @param violations 收集字段错误
  /// @return 日期，可空
  private static LocalDate finalDecisionOn(
      ExperienceInput input,
      FirstDecision firstDecision,
      FinalOutcome finalOutcome,
      LocalDate firstDecisionOn,
      LocalDate today,
      List<FieldViolation> violations) {
    if (isBlank(input.finalDecisionOn())) {
      return null;
    }
    if (finalOutcome == FinalOutcome.IN_PROGRESS
        || (firstDecision != null && firstDecision.isTerminal())) {
      violations.add(violation("finalDecisionOn", NOT_APPLICABLE, "这种情况不用填最终决定日期"));
      return null;
    }
    LocalDate date = date(input.finalDecisionOn(), "finalDecisionOn", today, violations);
    if (date != null && firstDecisionOn != null && date.isBefore(firstDecisionOn)) {
      violations.add(violation("finalDecisionOn", BEFORE_FIRST_DECISION, "最终决定日期不能早于首轮决定日期"));
      return null;
    }
    return date;
  }

  /// 审稿轮数：直接拒稿不该填；1–10；审后拒稿、首轮录用只能是 1。
  ///
  /// @param rounds 轮数，可空
  /// @param firstDecision 首轮结果，未解析成功为 `null`
  /// @param violations 收集字段错误
  /// @return 轮数，可空
  private static Integer reviewRounds(
      Integer rounds, FirstDecision firstDecision, List<FieldViolation> violations) {
    if (rounds == null) {
      return null;
    }
    if (firstDecision == FirstDecision.DESK_REJECT) {
      violations.add(violation("reviewRounds", NOT_APPLICABLE, "直接拒稿没有送审，不用填审稿轮数"));
      return null;
    }
    if (rounds < 1 || rounds > ExperienceRules.MAX_REVIEW_ROUNDS) {
      violations.add(violation("reviewRounds", OUT_OF_RANGE, "审稿轮数填 1 到 10 之间的整数"));
      return null;
    }
    if (firstDecision != null && firstDecision.isTerminal() && rounds != 1) {
      violations.add(violation("reviewRounds", INCONSISTENT, "首轮就有最终决定，审稿轮数只能是 1"));
      return null;
    }
    return rounds;
  }

  /// 审稿质量：直接拒稿不该填；1–5。
  ///
  /// @param quality 评分，可空
  /// @param firstDecision 首轮结果，未解析成功为 `null`
  /// @param violations 收集字段错误
  /// @return 评分，可空
  private static Integer reviewQuality(
      Integer quality, FirstDecision firstDecision, List<FieldViolation> violations) {
    if (quality != null && firstDecision == FirstDecision.DESK_REJECT) {
      violations.add(violation("reviewQuality", NOT_APPLICABLE, "直接拒稿没有送审，不用给审稿质量打分"));
      return null;
    }
    return rating(quality, "reviewQuality", violations);
  }

  /// 1–5 分。
  ///
  /// @param rating 评分，可空
  /// @param field 字段名
  /// @param violations 收集字段错误
  /// @return 评分，可空
  private static Integer rating(Integer rating, String field, List<FieldViolation> violations) {
    if (rating != null && (rating < 1 || rating > ExperienceRules.MAX_RATING)) {
      violations.add(violation(field, OUT_OF_RANGE, "评分是 1 到 5 的整数"));
      return null;
    }
    return rating;
  }

  /// 必填日期。
  ///
  /// @param raw 原文
  /// @param field 字段名
  /// @param today 今天
  /// @param violations 收集字段错误
  /// @return 日期；缺失或不合法为 `null`
  private static LocalDate requiredDate(
      String raw, String field, LocalDate today, List<FieldViolation> violations) {
    if (isBlank(raw)) {
      violations.add(violation(field, REQUIRED, "请填写日期"));
      return null;
    }
    return date(raw, field, today, violations);
  }

  /// 解析非空日期：写法、下界、不晚于今天。
  ///
  /// @param raw 原文
  /// @param field 字段名
  /// @param today 今天
  /// @param violations 收集字段错误
  /// @return 日期；不合法为 `null`
  private static LocalDate date(
      String raw, String field, LocalDate today, List<FieldViolation> violations) {
    LocalDate date;
    try {
      date = LocalDate.parse(raw.strip());
    } catch (DateTimeParseException ex) {
      violations.add(violation(field, INVALID, "日期写法不对"));
      return null;
    }
    if (date.isBefore(ExperienceRules.EARLIEST)) {
      violations.add(violation(field, TOO_EARLY, "日期不能早于 1990 年 1 月 1 日"));
      return null;
    }
    if (date.isAfter(today)) {
      violations.add(violation(field, FUTURE, "日期不能晚于今天"));
      return null;
    }
    return date;
  }

  /// 必填枚举（按常量名，区分大小写）。
  ///
  /// @param raw 原文
  /// @param field 字段名
  /// @param parser 按名字查常量
  /// @param violations 收集字段错误
  /// @param <E> 枚举类型
  /// @return 常量；缺失或未知为 `null`
  private static <E extends Enum<E>> E requiredEnum(
      String raw, String field, Function<String, E> parser, List<FieldViolation> violations) {
    if (isBlank(raw)) {
      violations.add(violation(field, REQUIRED, "请选择一项"));
      return null;
    }
    try {
      return parser.apply(raw.strip());
    } catch (IllegalArgumentException ex) {
      violations.add(violation(field, INVALID, "不认识的取值"));
      return null;
    }
  }

  /// 是否没填。
  ///
  /// @param raw 原文
  /// @return `null` 或全是空白时为 `true`
  private static boolean isBlank(String raw) {
    return raw == null || raw.isBlank();
  }

  /// 一条字段错误。
  ///
  /// @param field 字段名
  /// @param code 原因码
  /// @param message 默认文案
  /// @return 字段错误
  private static FieldViolation violation(String field, String code, String message) {
    return FieldViolation.of(field, code, message);
  }
}
