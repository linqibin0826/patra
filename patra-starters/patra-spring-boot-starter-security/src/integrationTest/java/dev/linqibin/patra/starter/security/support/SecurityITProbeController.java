package dev.linqibin.patra.starter.security.support;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.common.security.CurrentUserPort;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/// 集成测试专用的探针接口：把业务代码能观察到的东西原样返回。
@RestController
public class SecurityITProbeController {

  private final CurrentUserPort currentUserPort;
  private final SecurityITNoteDao noteDao;

  /// 创建探针控制器。
  ///
  /// @param currentUserPort 取当前用户的端口
  /// @param noteDao 测试实体的 Repository
  public SecurityITProbeController(CurrentUserPort currentUserPort, SecurityITNoteDao noteDao) {
    this.currentUserPort = currentUserPort;
    this.noteDao = noteDao;
  }

  /// 描述当前是谁。
  ///
  /// @return 匿名时是 `anonymous`，已登录时是 `用户ID:会话ID:账号类型:客户端类型`
  @GetMapping("/probe/whoami")
  public String whoAmI() {
    return currentUserPort.current().map(SecurityITProbeController::describe).orElse("anonymous");
  }

  /// 需要登录的接口。
  ///
  /// @return 当前用户的描述
  @GetMapping("/probe/me")
  public String me() {
    return describe(currentUserPort.require());
  }

  /// 业务代码判定「登录了但不允许」。
  ///
  /// @return 不会正常返回
  @GetMapping("/probe/forbidden-by-domain")
  public String forbiddenByDomain() {
    throw new SecurityITForbiddenException();
  }

  /// 控制器里直接抛出 Spring Security 的拒绝访问异常。
  ///
  /// @return 不会正常返回
  @GetMapping("/probe/access-denied")
  public String accessDenied() {
    throw new AccessDeniedException("probe: access denied");
  }

  /// 抛出一个与安全无关的异常，由全局异常处理器输出。
  ///
  /// @return 不会正常返回
  @GetMapping("/probe/boom")
  public String boom() {
    throw new UnsupportedOperationException("probe: boom");
  }

  /// 写一条记录，用来观察审计列。
  ///
  /// @return 新记录的 ID
  @PostMapping("/probe/notes")
  public String createNote() {
    SecurityITNoteEntity note = new SecurityITNoteEntity();
    note.setId(SnowflakeIdGenerator.getId());
    note.setContent("probe");
    noteDao.save(note);
    return Long.toString(note.getId());
  }

  /// 业务自己的登出接口，用来确认 `/logout` 路径没有被框架劫持。
  ///
  /// @return 固定文本
  @PostMapping("/logout")
  public String logout() {
    return "logout handled by controller";
  }

  /// 把当前用户拼成一行文本。
  ///
  /// @param user 当前用户
  /// @return `用户ID:会话ID:账号类型:客户端类型`
  private static String describe(CurrentUser user) {
    return user.userId()
        + ":"
        + user.sessionId()
        + ":"
        + user.accountType().getCode()
        + ":"
        + user.clientType().getCode();
  }
}
