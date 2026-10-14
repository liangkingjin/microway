package com.wtlx.infrastructure.memory.support;

/**
 * pgvector 文本字面量工具。
 * <p>
 * 为什么要手写字面量而不是用 {@code com.pgvector.PGvector}：{@code user_memory.embedding}
 * 的读写都发生在自研 JdbcTemplate SQL 里，用 {@code CAST(? AS vector)} 传文本字面量
 * 可以完全走标准 JDBC 参数绑定，不引入对 pgvector Java 类型注册时机（连接初始化）
 * 的隐式依赖，少一个"连接池换连接后类型丢失"的坑。
 * <p>
 * 格式与 pgvector 约定一致：{@code [0.1,0.2,0.3]}。{@code Float.toString} 与
 * {@code StringBuilder.append(float)} 的输出与 Locale 无关，不会出现逗号小数点。
 *
 * @author 金小梁
 */
public final class VectorLiterals {

    private VectorLiterals() {
    }

    /**
     * 把向量转成 pgvector 可解析的字面量
     *
     * @param vector 向量；为 null 或空时返回 null（SQL 侧写入 NULL 列）
     * @return 形如 {@code [0.1,0.2]} 的字符串
     */
    public static String toLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(vector.length * 12 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
