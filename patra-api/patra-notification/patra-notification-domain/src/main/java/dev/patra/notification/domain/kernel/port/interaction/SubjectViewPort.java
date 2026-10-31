package dev.patra.notification.domain.kernel.port.interaction;

import dev.patra.notification.domain.kernel.exception.InteractionUnavailableException;
import dev.patra.notification.domain.kernel.model.read.SubjectView;
import java.util.Collection;
import java.util.Map;

/// 按 interaction 登记表 ID 现查对象的展示信息（工程 spec 2.3：展示信息不进事件，读时组合）。站内列表与提醒邮件的
/// 「在哪」行、点开去哪都用它。经验另带结果（interaction 的两个内部接口由实现组合）。
public interface SubjectViewPort {

  /// 一次最多多少个对象，等于 interaction 接口的上限。
  int MAX_SUBJECTS = 100;

  /// 批量取对象视图。
  ///
  /// @param subjectIds 登记表 ID，1 ~ {@value #MAX_SUBJECTS} 个
  /// @return 登记表 ID → 视图；查不到的、不认识的类型省略
  /// @throws InteractionUnavailableException interaction 不可用（调用方自己决定降级还是传播）
  Map<Long, SubjectView> findViews(Collection<Long> subjectIds);
}
