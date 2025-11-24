package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.config.RedissonProperties;
import com.patra.starter.redisson.exception.LockExpressionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// 分布式锁键生成器。
///
/// 负责解析 {@link DistributedLock} 注解中的 SpEL 表达式，生成最终的 Redis 锁键。
///
/// ## 性能优化
///
/// - SpEL 表达式缓存：解析后的 Expression 对象会被缓存，避免重复解析
/// - 静态字符串检测：如果键不包含 SpEL 表达式，直接拼接前缀，跳过解析
///
/// @author Patra Team
/// @since 1.0.0
@Slf4j
@RequiredArgsConstructor
public class LockKeyGenerator {

    /// Redisson 配置属性
    private final RedissonProperties properties;

    /// SpEL 表达式解析器（线程安全）
    private final ExpressionParser parser = new SpelExpressionParser();

    /// SpEL 模板解析上下文。
    ///
    /// 支持在字符串中嵌入 SpEL 表达式，如 "user:#{#userId}"
    private static final ParserContext TEMPLATE_PARSER_CONTEXT = ParserContext.TEMPLATE_EXPRESSION;

    /// SpEL 表达式缓存。
    ///
    /// key: SpEL 表达式字符串, value: 解析后的 Expression 对象
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /// 生成完整的锁键。
    ///
    /// 流程：
    ///
    /// 1. 检测是否为静态字符串（无 SpEL 表达式），是则直接拼接前缀返回
    /// 2. 从缓存获取或解析 SpEL 表达式
    /// 3. 构建 SpEL 上下文，设置方法参数
    /// 4. 执行表达式，生成动态键
    /// 5. 拼接前缀，返回最终键
    ///
    /// @param keyExpression SpEL 表达式（来自 @DistributedLock 注解）
    /// @param method        目标方法
    /// @param args          方法参数
    /// @return 完整的锁键（包含前缀）
    /// @throws LockExpressionException SpEL 表达式解析失败时抛出
    public String generateKey(String keyExpression, Method method, Object[] args) {
        try {
            // 性能优化 1: 静态字符串检测
            if (isStaticString(keyExpression)) {
                String lockKey = properties.getLock().getKeyPrefix() + keyExpression;
                log.debug("生成静态锁键（无需 SpEL 解析）: {}", lockKey);
                return lockKey;
            }

            // 性能优化 2: 从缓存获取或解析 SpEL 表达式
            Expression expression = expressionCache.computeIfAbsent(
                keyExpression,
                key -> {
                    log.debug("首次解析 SpEL 模板表达式并缓存: {}", key);
                    return parser.parseExpression(key, TEMPLATE_PARSER_CONTEXT);
                }
            );

            // 构建 SpEL 上下文
            StandardEvaluationContext context = createEvaluationContext(method, args);

            // 执行表达式
            String evaluatedKey = expression.getValue(context, String.class);
            if (evaluatedKey == null) {
                throw new LockExpressionException(
                    "SpEL 表达式执行结果为 null",
                    keyExpression,
                    new NullPointerException("Expression evaluated to null")
                );
            }

            // 拼接前缀
            String lockKey = properties.getLock().getKeyPrefix() + evaluatedKey;
            log.debug("生成动态锁键（SpEL 解析）: {} -> {}", keyExpression, lockKey);
            return lockKey;

        } catch (Exception e) {
            log.error("SpEL 表达式解析失败: {}, 方法: {}", keyExpression, method.getName(), e);
            throw new LockExpressionException(keyExpression, e);
        }
    }

    /// 检测是否为静态字符串（不包含 SpEL 表达式）。
    ///
    /// 检查规则：不包含 "#{" 和 "${" 标记
    ///
    /// @param keyExpression 键表达式
    /// @return true 为静态字符串，false 包含 SpEL 表达式
    private boolean isStaticString(String keyExpression) {
        return !keyExpression.contains("#{") && !keyExpression.contains("${");
    }

    /// 创建 SpEL 上下文，设置方法参数。
    ///
    /// 支持以下变量：
    ///
    /// - `#参数名`：按参数名访问（需要编译时保留参数名）
    /// - `#参数.属性`：访问参数对象的属性
    ///
    /// @param method 目标方法
    /// @param args   方法参数
    /// @return SpEL 上下文
    private StandardEvaluationContext createEvaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 设置方法参数到上下文
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            context.setVariable(paramName, paramValue);
            log.trace("SpEL 上下文设置参数: {} = {}", paramName, paramValue);
        }

        return context;
    }

    /// 清除 SpEL 表达式缓存。
    ///
    /// 测试时使用，生产环境不需要调用。
    public void clearCache() {
        expressionCache.clear();
        log.debug("SpEL 表达式缓存已清除");
    }

    /// 获取缓存大小。
    ///
    /// 用于监控和测试。
    ///
    /// @return 缓存中的表达式数量
    public int getCacheSize() {
        return expressionCache.size();
    }
}
