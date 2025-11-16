package com.patra.common.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * 类型引用工具类 - 用于在运行时保持泛型类型信息
 *
 * <p><strong>设计背景</strong>：
 *
 * <p>Java 的泛型擦除机制会在运行时丢失泛型类型信息，例如：
 *
 * <pre>{@code
 * List<String> list = new ArrayList<>();
 * list.getClass(); // 返回 ArrayList.class，泛型信息 <String> 已丢失
 * }</pre>
 *
 * <p><strong>解决方案</strong>：
 *
 * <p>TypeReference 通过<strong>匿名内部类技巧</strong>保持运行时的泛型类型信息：
 *
 * <pre>{@code
 * // 创建类型引用
 * TypeReference<List<CanonicalPublication>> typeRef = new TypeReference<>() {};
 *
 * // 获取完整类型信息
 * Type type = typeRef.getType();  // ParameterizedType: List<CanonicalPublication>
 * Class<?> rawType = typeRef.getRawType();  // List.class
 * }</pre>
 *
 * <p><strong>使用场景</strong>：
 *
 * <ul>
 *   <li>多数据类型架构中的类型安全保障
 *   <li>ProvenanceDataPort 接口的泛型方法调用（fetchData 方法）
 *   <li>ProvenanceDataProvider 接口的泛型返回类型（ProviderResult&lt;T&gt;）
 *   <li>运行时类型验证和转换
 * </ul>
 *
 * <p><strong>设计原理</strong>：
 *
 * <ol>
 *   <li>TypeReference 是抽象类，强制使用匿名内部类创建实例
 *   <li>通过 {@code getClass().getGenericSuperclass()} 获取泛型超类
 *   <li>从泛型超类中提取实际类型参数
 *   <li>保存完整的 {@link Type} 信息（包括泛型参数）
 * </ol>
 *
 * <p><strong>注意事项</strong>：
 *
 * <ul>
 *   <li>必须使用匿名内部类创建实例：{@code new TypeReference<T>() {}}
 *   <li>不能直接实例化：{@code new TypeReference<T>()} 会抛异常
 *   <li>支持嵌套泛型：{@code Map<String, List<CanonicalPublication>>}
 * </ul>
 *
 * <p><strong>类似设计参考</strong>：
 *
 * <ul>
 *   <li>Jackson: {@code com.fasterxml.jackson.core.type.TypeReference}
 *   <li>Gson: {@code com.google.gson.reflect.TypeToken}
 *   <li>Spring: {@code org.springframework.core.ParameterizedTypeReference}
 * </ul>
 *
 * @param <T> 要引用的类型
 * @author Patra Architecture Team
 * @since 0.1.0
 * @see java.lang.reflect.Type
 * @see java.lang.reflect.ParameterizedType
 */
public abstract class TypeReference<T> {

  /** 完整的类型信息（包含泛型参数） */
  private final Type type;

  /**
   * 受保护的构造函数，只能通过匿名内部类调用
   *
   * <p><strong>工作原理</strong>：
   *
   * <ol>
   *   <li>通过反射获取当前类的泛型超类
   *   <li>验证超类是参数化类型（ParameterizedType）
   *   <li>提取第一个类型参数作为引用类型
   * </ol>
   *
   * <p><strong>示例</strong>：
   *
   * <pre>{@code
   * // 正确用法：匿名内部类
   * new TypeReference<List<String>>() {};
   *
   * // 错误用法：直接实例化（会抛异常）
   * new TypeReference<List<String>>();
   * }</pre>
   *
   * @throws IllegalStateException 如果未使用匿名内部类创建（即未提供泛型参数）
   */
  protected TypeReference() {
    // 获取匿名内部类的泛型超类
    Type superclass = getClass().getGenericSuperclass();

    // 验证是否使用了匿名内部类（泛型超类应该是 ParameterizedType）
    if (!(superclass instanceof ParameterizedType)) {
      throw new IllegalStateException(
          "TypeReference必须使用匿名内部类创建，例如: new TypeReference<YourType>() {}");
    }

    // 提取类型参数
    ParameterizedType parameterizedType = (ParameterizedType) superclass;
    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

    // 验证类型参数存在
    if (actualTypeArguments.length == 0) {
      throw new IllegalStateException("TypeReference必须提供泛型参数");
    }

    // 保存第一个类型参数
    this.type = actualTypeArguments[0];
  }

  /**
   * 获取完整的类型信息（包括泛型参数）
   *
   * <p><strong>返回值类型</strong>：
   *
   * <ul>
   *   <li>简单类型：{@code Class} 实例（例如：{@code CanonicalPublication.class}）
   *   <li>参数化类型：{@code ParameterizedType} 实例（例如：{@code List<CanonicalPublication>}）
   * </ul>
   *
   * <p><strong>示例</strong>：
   *
   * <pre>{@code
   * TypeReference<List<CanonicalPublication>> ref = new TypeReference<>() {};
   * Type type = ref.getType();
   * // type instanceof ParameterizedType
   * // type.toString() = "java.util.List<com.patra.common.model.CanonicalPublication>"
   * }</pre>
   *
   * @return 完整的类型信息
   */
  public Type getType() {
    return type;
  }

  /**
   * 获取原始类型（擦除泛型参数）
   *
   * <p><strong>功能说明</strong>：
   *
   * <ul>
   *   <li>对于简单类型：直接返回 Class 对象
   *   <li>对于参数化类型：提取原始类型（例如：{@code List<T>} → {@code List.class}）
   * </ul>
   *
   * <p><strong>示例</strong>：
   *
   * <pre>{@code
   * TypeReference<List<CanonicalPublication>> ref = new TypeReference<>() {};
   * Class<?> rawType = ref.getRawType();
   * // rawType = List.class
   * }</pre>
   *
   * @return 原始类型的 Class 对象
   */
  public Class<?> getRawType() {
    return extractRawType(type);
  }

  /**
   * 从 Type 中提取原始类型
   *
   * <p><strong>处理逻辑</strong>：
   *
   * <ul>
   *   <li>{@code Class} 类型：直接返回
   *   <li>{@code ParameterizedType}：提取 rawType 字段
   *   <li>其他类型（通配符、类型变量等）：抛出异常
   * </ul>
   *
   * @param type 类型信息
   * @return 原始类型的 Class 对象
   * @throws IllegalArgumentException 如果无法提取原始类型
   */
  private Class<?> extractRawType(Type type) {
    if (type instanceof Class) {
      // 简单类型：如 CanonicalPublication.class
      return (Class<?>) type;
    } else if (type instanceof ParameterizedType) {
      // 参数化类型：如 List<CanonicalPublication>
      ParameterizedType parameterizedType = (ParameterizedType) type;
      return (Class<?>) parameterizedType.getRawType();
    } else {
      // 其他类型（通配符、类型变量等）
      throw new IllegalArgumentException("不支持的类型: " + type);
    }
  }

  /**
   * 检查指定类是否可以赋值给此 TypeReference 引用的类型
   *
   * <p><strong>应用场景</strong>：
   *
   * <ul>
   *   <li>运行时类型验证
   *   <li>多态类型检查
   *   <li>数据转换前的类型兼容性校验
   * </ul>
   *
   * <p><strong>示例</strong>：
   *
   * <pre>{@code
   * TypeReference<CanonicalPublication> ref = new TypeReference<>() {};
   * ref.isAssignableFrom(CanonicalPublication.class);  // true
   * ref.isAssignableFrom(SubLiterature.class);        // true（如果 SubLiterature 继承 CanonicalPublication）
   * ref.isAssignableFrom(Journal.class);              // false
   * }</pre>
   *
   * @param clazz 要检查的类
   * @return 如果可以赋值则返回 true
   * @throws NullPointerException 如果 clazz 为 null
   */
  public boolean isAssignableFrom(Class<?> clazz) {
    Objects.requireNonNull(clazz, "类不能为空");
    Class<?> rawType = getRawType();
    return rawType.isAssignableFrom(clazz);
  }

  /**
   * 判断两个 TypeReference 是否相等
   *
   * <p><strong>相等规则</strong>：
   *
   * <ul>
   *   <li>引用的类型（type）相同
   *   <li>泛型参数完全匹配
   * </ul>
   *
   * <p><strong>注意</strong>：
   *
   * <ul>
   *   <li>不比较 getClass()，因为匿名内部类每次创建都是不同的类
   *   <li>只比较引用的类型（type），确保语义相等
   * </ul>
   *
   * <p><strong>示例</strong>：
   *
   * <pre>{@code
   * TypeReference<List<String>> ref1 = new TypeReference<>() {};
   * TypeReference<List<String>> ref2 = new TypeReference<>() {};
   * ref1.equals(ref2); // true（引用相同类型）
   *
   * TypeReference<List<Integer>> ref3 = new TypeReference<>() {};
   * ref1.equals(ref3); // false（泛型参数不同）
   * }</pre>
   *
   * @param o 要比较的对象
   * @return 如果相等则返回 true
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeReference)) return false;
    TypeReference<?> that = (TypeReference<?>) o;
    return Objects.equals(type, that.type);
  }

  /**
   * 计算哈希码
   *
   * <p>基于引用的类型（type）计算哈希值
   *
   * @return 哈希码
   */
  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  /**
   * 返回类型引用的字符串表示
   *
   * <p><strong>格式</strong>：{@code TypeReference{完整类型名称}}
   *
   * <p><strong>示例</strong>：
   *
   * <pre>
   * TypeReference<List<String>> ref = new TypeReference<>() {};
   * ref.toString(); // "TypeReference{java.util.List<java.lang.String>}"
   * </pre>
   *
   * @return 字符串表示
   */
  @Override
  public String toString() {
    return "TypeReference{" + type + "}";
  }
}
