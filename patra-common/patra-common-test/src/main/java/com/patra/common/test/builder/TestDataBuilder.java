package com.patra.common.test.builder;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 测试数据构建器抽象基类
 *
 * <p>提供通用的测试数据构建能力,支持链式调用和批量构建。
 * 子类需要实现 {@link #build()} 方法来定义具体的对象构建逻辑。</p>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li>Builder 模式: 支持流式 API 和链式调用</li>
 *   <li>模板方法模式: 定义通用构建流程,具体构建逻辑由子类实现</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class UserTestBuilder extends TestDataBuilder<User> {
 *     private String name = "Test User";
 *     private String email = "test@example.com";
 *
 *     public UserTestBuilder withName(String name) {
 *         this.name = name;
 *         return this;
 *     }
 *
 *     public UserTestBuilder withEmail(String email) {
 *         this.email = email;
 *         return this;
 *     }
 *
 *     @Override
 *     public User build() {
 *         return new User(name, email);
 *     }
 * }
 *
 * // 使用方式
 * User user = new UserTestBuilder()
 *     .withName("Alice")
 *     .build();
 *
 * List<User> users = new UserTestBuilder()
 *     .buildList(10);
 * }</pre>
 *
 * @param <T> 构建的目标对象类型
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public abstract class TestDataBuilder<T> {

    /**
     * 构建单个对象
     *
     * <p>子类必须实现此方法,定义具体的对象构建逻辑。
     * 建议为所有字段提供合理的默认值,以简化测试代码。</p>
     *
     * @return 构建的对象实例
     */
    public abstract T build();

    /**
     * 批量构建多个对象
     *
     * <p>通过多次调用 {@link #build()} 方法生成多个对象实例。
     * 每次调用都会创建一个新的对象。</p>
     *
     * @param count 构建数量,必须大于 0
     * @return 对象列表
     * @throws IllegalArgumentException 如果 count <= 0
     */
    public List<T> buildList(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("构建数量必须大于 0, 实际值: " + count);
        }
        return IntStream.range(0, count)
            .mapToObj(i -> build())
            .collect(Collectors.toList());
    }

    /**
     * 构建并保存到仓储
     *
     * <p>默认实现抛出 {@link UnsupportedOperationException},
     * 子类可以根据需要覆盖此方法以支持保存到数据库。</p>
     *
     * <h4>使用示例</h4>
     * <pre>{@code
     * public class UserTestBuilder extends TestDataBuilder<User> {
     *     @Override
     *     public User buildAndSave(Object repository) {
     *         User user = build();
     *         ((UserRepository) repository).save(user);
     *         return user;
     *     }
     * }
     * }</pre>
     *
     * @param repository 仓储实例
     * @return 保存后的对象
     * @throws UnsupportedOperationException 如果子类未实现此方法
     */
    public T buildAndSave(Object repository) {
        throw new UnsupportedOperationException("子类需要实现 buildAndSave() 方法以支持保存功能");
    }
}
