package dev.linqibin.patra.catalog.adapter.rest.portal.request;

import java.beans.PropertyEditorSupport;
import java.util.Arrays;
import java.util.List;

/// 把 query 重复参数绑定为 `List`，且**不按逗号拆分**单个值的 PropertyEditor。
///
/// 真实文献类型值含逗号（`Clinical Trial, Phase III`），Spring 默认的 `String→List` 转换会把它切成两个值。
/// 必须同时覆写两个入口：Spring 的 `TypeConverterDelegate` 在走 [#setAsText] 之前会先把 `String[]`
/// 用逗号 join 成一个 String，只覆写 `setAsText` 会让重复参数合并成 `"A,B"` 一个元素；
/// 覆写 [#setValue] 在 join 之前拦住 `String[]`。元素级类型转换（如 `List<Long>`）由后续
/// ConversionService 完成。
///
/// @author linqibin
/// @since 0.1.0
public class RepeatedParamListEditor extends PropertyEditorSupport {

  /// 单个参数值 → 单元素列表；null / 空白（如 `?venue=`）→ 空列表。不拆逗号。
  ///
  /// 空白必须在这里丢掉：空串交给后续 `String→Long` 转换会变成 null 元素，record 的 `List.copyOf` 随即 NPE → 500。
  ///
  /// @param text 单个参数值
  @Override
  public void setAsText(String text) {
    super.setValue(text == null || text.isBlank() ? List.of() : List.of(text));
  }

  /// `String[]`（重复参数）→ 逐元素列表，丢弃空白元素（`venue=1&venue=`）；单个 `String` 同 [#setAsText]；其他类型原样透传。
  ///
  /// @param value 绑定源值
  @Override
  public void setValue(Object value) {
    if (value instanceof String[] values) {
      super.setValue(Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList());
    } else if (value instanceof String single) {
      setAsText(single);
    } else {
      super.setValue(value);
    }
  }
}
