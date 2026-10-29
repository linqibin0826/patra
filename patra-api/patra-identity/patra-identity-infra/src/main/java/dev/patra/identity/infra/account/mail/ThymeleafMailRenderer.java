package dev.patra.identity.infra.account.mail;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/// 用 Thymeleaf 按模板名渲染邮件的文本与 HTML 两部分。
///
/// 两个类路径解析器按模板名前缀分流：`html/*` 是 HTML 模式、`mail/html/<名>.html`；`text/*` 是 TEXT 模式、
/// `mail/text/<名>.txt`。模板变量是邮件自己的 `model()` 加公共变量 `siteName`、`securityUrl`、`subject`；
/// 变量 `notice` 若是 {@link SecurityNotice}，换成按时区格式化好的 {@link SecurityNoticeView}；值是 `Instant` 的变量
/// （如注销到期时刻）换成同一写法的时间文字，形如 `2026-11-04 08:00 (UTC+8)`。
/// 自建引擎、不用 starter：identity 没有页面，不需要 MVC 视图解析器。
public final class ThymeleafMailRenderer {

  /// 站名，模板里的 `siteName`。
  static final String SITE_NAME = "Patra";

  private static final String TEMPLATE_ROOT = "mail/";

  private final TemplateEngine engine;
  private final MailLinkPolicy links;
  private final ZoneId zone;

  /// 创建渲染器。
  ///
  /// @param links 链接规则，给出安全设置页的地址
  /// @param zone 安全通知里时间的显示时区
  public ThymeleafMailRenderer(MailLinkPolicy links, ZoneId zone) {
    this.links = Objects.requireNonNull(links, "links 不能为 null");
    this.zone = Objects.requireNonNull(zone, "zone 不能为 null");
    this.engine = new TemplateEngine();
    engine.addTemplateResolver(resolver("html/*", ".html", TemplateMode.HTML, 1));
    engine.addTemplateResolver(resolver("text/*", ".txt", TemplateMode.TEXT, 2));
  }

  /// 渲染一封邮件。
  ///
  /// @param mail 邮件
  /// @return 主题、文本、HTML
  public RenderedMail render(OutgoingMail mail) {
    Objects.requireNonNull(mail, "mail 不能为 null");
    Map<String, Object> variables = new HashMap<>(mail.model());
    variables.replaceAll(
        (name, value) ->
            value instanceof Instant instant
                ? SecurityNoticeView.formatTime(instant, zone)
                : value);
    variables.put("siteName", SITE_NAME);
    variables.put("securityUrl", links.securitySettingsUrl());
    variables.put("subject", mail.subject());
    if (variables.get("notice") instanceof SecurityNotice notice) {
      variables.put("notice", SecurityNoticeView.from(notice, zone));
    }
    return RenderedMail.of(
        mail.subject(),
        engine.process(
            "text/" + mail.template(), new Context(Locale.SIMPLIFIED_CHINESE, variables)),
        engine.process(
            "html/" + mail.template(), new Context(Locale.SIMPLIFIED_CHINESE, variables)));
  }

  /// 一个按模板名前缀分流的类路径解析器。
  ///
  /// @param pattern 可解析的模板名模式
  /// @param suffix 文件后缀
  /// @param mode 模板模式
  /// @param order 顺序
  /// @return 解析器
  private static ClassLoaderTemplateResolver resolver(
      String pattern, String suffix, TemplateMode mode, int order) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix(TEMPLATE_ROOT);
    resolver.setSuffix(suffix);
    resolver.setTemplateMode(mode);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setResolvablePatterns(Set.of(pattern));
    resolver.setOrder(order);
    resolver.setCacheable(true);
    return resolver;
  }
}
