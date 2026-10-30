/// mail 子域的应用层。
///
/// 邮件：渠道部分（模板、SMTP 与幂等键、重试与死信、发送记录，发 identity 的安全邮件）与提醒邮件部分
/// （离站逐条、每日汇总、退订头）（PAP-105、PAP-146）。
///
/// 其他子域只能用它的公开包：`domain.mail.port.read`、`domain.mail.model.read`、`domain.mail.event`。
package dev.patra.notification.app.mail;
