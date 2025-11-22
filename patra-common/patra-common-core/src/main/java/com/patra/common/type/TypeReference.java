package com.patra.common.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/// 类型引用工具类 - 用于在运行时保持泛型类型信息
///
/// **设计背景**：
///
/// Java 的泛型擦除机制会在运行时丢失泛型类型信息，例如：
///
/// ```java
/// List<String> list = new ArrayList<>();
/// list.getClass(); // 返回 ArrayList.class，泛型信息 <String> 已丢失
/// ```
///
/// **解决方案**：
///
/// TypeReference 通过**匿名内部类技巧**保持运行时的泛型类型信息：
///
/// ```java
/// // 创建类型引用
/// TypeReference<List<CanonicalPublication>> typeRef = new TypeReference<>() {;
///
/// // 获取完整类型信息
/// Type type = typeRef.getType();  // ParameterizedType: List<CanonicalPublication>
/// Class<?> rawType = typeRef.getRawType();  // List.class
/// ```
///
/// **使用场景**：
///
/// - 多数据类型架构中的类型安全保障
///   - ProvenanceDataPort 接口的泛型方法调用（fetchData 方法）
///   - ProvenanceDataProvider 接口的泛型返回类型（ProviderResult&lt;T&gt;）
///   - 运行时类型验证和转换
///
/// **设计原理**：
///
/// **注意事项**：
///
/// - 必须使用匿名内部类创建实例：`new TypeReference<T>() {`}
///   - 不能直接实例化：`new TypeReference<T>()` 会抛异常
///   - 支持嵌套泛型：`Map<String, List<CanonicalPublication>>`
///
/// **类似设计参考**：
///
/// - Jackson: `com.fasterxml.jackson.core.type.TypeReference`
///   - Gson: `com.google.gson.reflect.TypeToken`
///   - Spring: `org.springframework.core.ParameterizedTypeReference`
///
/// @param <T> 要引用的类型
/// @author linqibin
/// @since 0.1.0
/// @see java.lang.reflect.Type
/// @see java.lang.reflect.ParameterizedType
public abstract class TypeReference<T> {

  /// 完整的类型信息（包含泛型参数）
  private final Type type;

  /// 受保护的构造函数，只能通过匿名内部类调用
  ///
  /// **工作原理**：
  ///
  /// **示例**：
  ///
  /// ```java
  /// // 正确用法：匿名内部类
  /// new TypeReference<List<String>>() {;
  ///
  /// // 错误用法：直接实例化（会抛异常）
  /// new TypeReference<List<String>>();
  /// ```
  ///
  /// @throws IllegalStateException 如果未使用匿名内部类创建（即未提供泛型参数）
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

  /// 获取完整的类型信息（包括泛型参数）
  ///
  /// **返回值类型**：
  ///
  /// - 简单类型：`Class` 实例（例如：`CanonicalPublication.class`）
  ///   - 参数化类型：`ParameterizedType` 实例（例如：`List<CanonicalPublication>`）
  ///
  /// **示例**：
  ///
  /// ```java
  /// TypeReference<List<CanonicalPublication>> ref = new TypeReference<>() {;
  /// Type type = ref.getType();
  /// // type instanceof ParameterizedType
  /// // type.toString() = "java.util.List<com.patra.common.model.CanonicalPublication>"
  /// ```
  ///
  /// @return 完整的类型信息
  public Type getType() {
    return type;
  }

  /// 获取原始类型（擦除泛型参数）
  ///
  /// **功能说明**：
  ///
  /// - 对于简单类型：直接返回 Class 对象
  ///   - 对于参数化类型：提取原始类型（例如：`List<T>` → `List.class`）
  ///
  /// **示例**：
  ///
  /// ```java
  /// TypeReference<List<CanonicalPublication>> ref = new TypeReference<>() {;
  /// Class<?> rawType = ref.getRawType();
  /// // rawType = List.class
  /// ```
  ///
  /// @return 原始类型的 Class 对象
  public Class<?> getRawType() {
    return extractRawType(type);
  }

  /// 从 Type 中提取原始类型
  ///
  /// **处理逻辑**：
  ///
  /// - `Class` 类型：直接返回
  ///   - `ParameterizedType`：提取 rawType 字段
  ///   - 其他类型（通配符、类型变量等）：抛出异常
  ///
  /// @param type 类型信息
  /// @return 原始类型的 Class 对象
  /// @throws IllegalArgumentException 如果无法提取原始类型
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

  /// 检查指定类是否可以赋值给此 TypeReference 引用的类型
  ///
  /// **应用场景**：
  ///
  /// - 运行时类型验证
  ///   - 多态类型检查
  ///   - 数据转换前的类型兼容性校验
  ///
  /// **示例**：
  ///
  /// ```java
  /// TypeReference<CanonicalPublication> ref = new TypeReference<>() {;
  /// ref.isAssignableFrom(CanonicalPublication.class);  // true
  /// ref.isAssignableFrom(SubPublication.class);        // true（如果 SubPublication 继承
  // CanonicalPublication）
  /// ref.isAssignableFrom(Journal.class);              // false
  /// ```
  ///
  /// @param clazz 要检查的类
  /// @return 如果可以赋值则返回 true
  /// @throws NullPointerException 如果 clazz 为 null
  public boolean isAssignableFrom(Class<?> clazz) {
    Objects.requireNonNull(clazz, "类不能为空");
    Class<?> rawType = getRawType();
    return rawType.isAssignableFrom(clazz);
  }

  /// 判断两个 TypeReference 是否相等
  ///
  /// **相等规则**：
  ///
  /// - 引用的类型（type）相同
  ///   - 泛型参数完全匹配
  ///
  /// **注意**：
  ///
  /// - 不比较 getClass()，因为匿名内部类每次创建都是不同的类
  ///   - 只比较引用的类型（type），确保语义相等
  ///
  /// **示例**：
  ///
  /// ```java
  /// TypeReference<List<String>> ref1 = new TypeReference<>() {;
  /// TypeReference<List<String>> ref2 = new TypeReference<>() {;
  /// ref1.equals(ref2); // true（引用相同类型）
  ///
  /// TypeReference<List<Integer>> ref3 = new TypeReference<>() {;
  /// ref1.equals(ref3); // false（泛型参数不同）
  /// ```
  ///
  /// @param o 要比较的对象
  /// @return 如果相等则返回 true
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeReference)) return false;
    TypeReference<?> that = (TypeReference<?>) o;
    return Objects.equals(type, that.type);
  }

  /// 计算哈希码
  ///
  /// 基于引用的类型（type）计算哈希值
  ///
  /// @return 哈希码
  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  /// 返回类型引用的字符串表示
  ///
  /// **格式**：`TypeReference{完整类型名称`}
  ///
  /// **示例**：
  ///
  /// ```
  ///
  /// TypeReference<List<String>> ref = new TypeReference<>() {};
  /// ref.toString(); // "TypeReference{java.util.List<java.lang.String>}"
  ///
  /// ```
  ///
  /// @return 字符串表示
  @Override
  public String toString() {
    return "TypeReference{" + type + "}";
  }
}
