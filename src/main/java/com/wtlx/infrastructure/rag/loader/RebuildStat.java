package com.wtlx.infrastructure.rag.loader;

import lombok.Data;

/**
 * 全量回填统计
 *
 * @author 金小梁
 */
@Data
public class RebuildStat {

    private int total;

    private int indexed;

    private int unchanged;

    private int failed;

    public void incrIndexed() {
        indexed++;
    }

    public void incrUnchanged() {
        unchanged++;
    }

    public void incrFailed() {
        failed++;
    }

    @Override
    public String toString() {
        return "total=" + total + ", indexed=" + indexed + ", unchanged=" + unchanged + ", failed=" + failed;
    }
}
