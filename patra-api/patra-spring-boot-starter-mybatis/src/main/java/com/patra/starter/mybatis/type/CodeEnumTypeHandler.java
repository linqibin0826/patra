package com.patra.starter.mybatis.type;

import com.patra.common.enums.CodeEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.EnumTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 枚举统一处理器（默认枚举处理器）。
 *
 * <p>设计目标：
 * <ul>
 *   <li>让 DO/Entity 中可以<strong>直接使用领域枚举类型</strong>（实现 {@link CodeEnum}），无需额外注解或 XML。</li>
 *   <li>保证<strong>老代码/第三方</strong>未实现 {@code CodeEnum} 的枚举也能正常工作（回退到 {@link EnumTypeHandler}）。</li>
 *   <li>读取时提供<strong>宽松匹配</strong>：支持数字与字符串之间的等价判断（如列值 "1" ↔ 代码 1）。</li>
 * </ul>
 *
 * <p>行为约定：
 * <ol>
 *   <li><b>写入</b>：若枚举实现 {@code CodeEnum&lt;C&gt;}，则使用 {@code getCode()} 的返回值写入列；否则回退为默认的枚举持久化（通常为 {@code name()}）。</li>
 *   <li><b>读取</b>：若目标类型实现 {@code CodeEnum}，将列值与每个枚举常量的 {@code getCode()} 做匹配；支持：
 *       <ul>
 *         <li>对象直接相等（{@code equals}）；</li>
 *         <li>数字宽松比较（{@code Number.longValue()} 等值）；</li>
 *         <li>字符串化比较（列值字符串与 {@code code.toString()} 相等）。</li>
 *       </ul>
 *       若目标类型不是 {@code CodeEnum}，尝试以 {@code name()} 匹配；失败后再尝试以 {@code ordinal()} 匹配。</li>
 *   </li>
 * </ol>
 *
 * <p>使用方式：
 * <ul>
 *   <li>将本类注册为 MyBatis 的 <b>默认枚举处理器</b>（例如在配置中调用 {@code configuration.setDefaultEnumTypeHandler(CodeEnumTypeHandler.class)}）。</li>
 *   <li>数据库列类型可为 {@code tinyint/int/bigint/varchar/enum/json} 等，只要与 {@code getCode()} 的返回值能互转。</li>
 * </ul>
 *
 * <p>边界与注意事项：
 * <ul>
 *   <li>不支持“复合 code”映射（如多列/对象作为 code）。如需此能力，请为该枚举或字段单独实现自定义 {@code TypeHandler}。</li>
 *   <li>当同一枚举既希望部分字段以 {@code name()} 存储、部分以 {@code code} 存储时，建议在字段级别声明专用 {@code TypeHandler}，避免歧义。</li>
 *   <li>本类无共享可变状态，<b>线程安全</b>；可单例使用。</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see CodeEnum
 */
@MappedTypes(Enum.class) // 告诉 MyBatis：该处理器适用于所有枚举类型
public class CodeEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    /**
     * 目标枚举类型（由 MyBatis 通过构造器注入）
     */
    private final Class<E> type;

    /**
     * 回退处理器：当目标枚举未实现 CodeEnum 时，委托给 MyBatis 默认的枚举处理器
     * （默认行为通常是 name()，具体取决于全局配置）。
     */
    private final EnumTypeHandler<E> delegate;

    /**
     * 由 MyBatis 反射创建实例时传入目标枚举类型。
     *
     * @param type 目标枚举的 Class；不能为空
     */
    public CodeEnumTypeHandler(Class<E> type) {
        if (type == null) throw new IllegalArgumentException("Type cannot be null");
        this.type = type;
        this.delegate = new EnumTypeHandler<>(type);
    }

    // ---------- 写入（Java -> JDBC） ----------

    /**
     * 将枚举参数写入 SQL 语句。
     *
     * <p>实现了 {@code CodeEnum}：写入其 {@code getCode()} 的值；
     * 否则：回退给 {@link EnumTypeHandler}（通常写入 {@code name()}）。</p>
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        if (parameter instanceof CodeEnum<?> ce) {
            Object code = ce.getCode();
            if (code == null) {
                ps.setObject(i, null);
                return;
            }
            // 如果声明了 JDBC 类型则使用之，否则交给驱动做类型推断
            if (jdbcType != null) {
                ps.setObject(i, code, jdbcType.TYPE_CODE);
            } else {
                ps.setObject(i, code);
            }
        } else {
            // 非 CodeEnum：退回默认枚举策略（通常写入 name）
            delegate.setNonNullParameter(ps, i, parameter, jdbcType);
        }
    }

    // ---------- 读取（JDBC -> Java） ----------

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object v = rs.getObject(columnName);
        return parse(v);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object v = rs.getObject(columnIndex);
        return parse(v);
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object v = cs.getObject(columnIndex);
        return parse(v);
    }

    /**
     * 将数据库原始值解析为目标枚举常量。
     *
     * <p>实现 {@code CodeEnum} 时按 code 匹配，包含数字/字符串的宽松比较；
     * 否则先按 {@code name()}，再按 {@code ordinal()} 兜底。</p>
     *
     * @throws SQLException 当找不到匹配项或值非法时抛出
     */
    private E parse(Object value) throws SQLException {
        if (value == null) return null;

        // 目标类型实现了 CodeEnum：按 code 匹配
        if (CodeEnum.class.isAssignableFrom(type)) {
            for (E e : type.getEnumConstants()) {
                CodeEnum<?> ce = (CodeEnum<?>) e;
                Object code = ce.getCode();
                if (code == null) continue;

                // 1) 直接 equals
                if (value.equals(code)) return e;

                // 2) 数字宽松比较："1" ↔ 1
                if (value instanceof Number v1 && code instanceof Number v2) {
                    if (v1.longValue() == v2.longValue()) return e;
                }

                // 3) 字符串化比较："ENABLED" ↔ ENABLED,  "1" ↔ 1.toString()
                if (value instanceof String s) {
                    if (s.equals(code.toString())) return e;
                }
            }
            throw new SQLException("Unknown code '" + value + "' for enum " + type.getName());
        }

        // 非 CodeEnum：尝试以 name() 匹配，失败再以 ordinal() 兜底
        E[] constants = type.getEnumConstants();
        String s = String.valueOf(value);

        for (E e : constants) {
            if (e.name().equals(s)) return e;
        }
        try {
            int ord = Integer.parseInt(s);
            if (ord >= 0 && ord < constants.length) return constants[ord];
        } catch (NumberFormatException ignored) {
            // 忽略，继续抛出统一异常
        }
        throw new SQLException("Cannot map value '" + value + "' to enum " + type.getName());
    }
}
