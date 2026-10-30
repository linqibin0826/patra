/// preference 子域的领域层。
///
/// 提醒偏好与一键退订：每类提醒的邮件档位（关、离站逐条、每日汇总），每个用户一个退订令牌，
/// RFC 8058 一键退订，退订落地页的数据（PAP-142）。
///
/// 其他子域只能用它的公开包：`domain.preference.port.read`、`domain.preference.model.read`、
/// `domain.preference.event`。
package dev.patra.notification.domain.preference;
