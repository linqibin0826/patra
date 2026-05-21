package dev.linqibin.starter.restclient.download;

/// FTP 登录凭证。
///
/// @param username 用户名
/// @param password 密码
/// @author linqibin
/// @since 0.1.0
public record FtpCredentials(String username, String password) {}
