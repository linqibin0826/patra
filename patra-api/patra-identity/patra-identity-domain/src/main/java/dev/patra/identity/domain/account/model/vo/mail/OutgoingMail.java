package dev.patra.identity.domain.account.model.vo.mail;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Map;

/// 一封要发出去的邮件：收件人、模板名、主题和模板变量。
///
/// 适配器按模板名找到文本和 HTML 两份模板、用变量渲染，不认识具体的邮件类型；加一种新邮件就是加一个
/// 实现这个接口的记录和两份模板，不改适配器。安全通知邮件把 {@link SecurityNotice} 放在变量 `notice` 下，
/// 模板里引用共用的片段。
public interface OutgoingMail {

  /// 收件人。
  ///
  /// @return 收件人邮箱
  EmailAddress to();

  /// 模板名，例如 `email-verification`；适配器据此找 `html/<模板名>` 与 `text/<模板名>`。
  ///
  /// @return 模板名
  String template();

  /// 邮件主题。
  ///
  /// @return 主题
  String subject();

  /// 模板变量。
  ///
  /// @return 变量名到值的映射，不可变
  Map<String, Object> model();
}
