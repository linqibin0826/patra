package com.patra.common.test.base;

/**
 * 单元测试基类
 *
 * <p>提供通用的单元测试配置和工具方法。所有单元测试类都应该继承此基类,
 * 以获得统一的测试配置。</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>提供便捷的测试工具方法</li>
 *   <li>统一的测试生命周期管理</li>
 *   <li>配合 @ExtendWith(MockitoExtension.class) 使用以获得 Mockito 支持</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @ExtendWith(MockitoExtension.class)
 * class TaskAggregateTest extends BaseUnitTest {
 *
 *     @Mock
 *     private TaskRepository taskRepository;
 *
 *     @InjectMocks
 *     private TaskOrchestrator taskOrchestrator;
 *
 *     @Test
 *     void testCreateTask() {
 *         // given
 *         TaskCreateCommand command = new TaskCreateCommand("Test Task");
 *
 *         // when
 *         Task task = taskOrchestrator.createTask(command);
 *
 *         // then
 *         assertThat(task.getTitle()).isEqualTo("Test Task");
 *         verify(taskRepository, times(1)).save(any(Task.class));
 *     }
 * }
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
public abstract class BaseUnitTest {

    /**
     * 默认构造函数
     */
    protected BaseUnitTest() {
        // 子类可以覆盖以添加自定义初始化逻辑
    }
}
