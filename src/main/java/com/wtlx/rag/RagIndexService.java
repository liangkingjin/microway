package com.wtlx.rag;

import java.util.List;

/**
 * RAG 索引服务：负责单个来源的切片、向量化与入库。
 *
 * @author 金小梁
 */
public interface RagIndexService {

    /**
     * 建立或刷新一个来源的向量索引（幂等）
     *
     * @param docType  来源类型，见 {@link com.wtlx.constant.RagDocType}
     * @param sourceId 来源业务主键
     * @return 索引结果
     */
    RagIndexResult index(String docType, Long sourceId);

    /**
     * 删除一个来源的全部切片与索引记录
     *
     * @param docType  来源类型
     * @param sourceId 来源业务主键
     */
    void remove(String docType, Long sourceId);

    /**
     * 索引结果
     */
    class RagIndexResult {
        /**
         * 结果状态：INDEXED=新写入，UNCHANGED=内容未变跳过，EMPTY=无正文可切片，NOT_FOUND=来源不存在
         */
        public enum Status {
            /** 已写入 */
            INDEXED,
            /** 内容未变，跳过 */
            UNCHANGED,
            /** 无正文可切片 */
            EMPTY,
            /** 来源不存在 */
            NOT_FOUND
        }

        private final Status status;
        private final int chunkCount;

        public RagIndexResult(Status status, int chunkCount) {
            this.status = status;
            this.chunkCount = chunkCount;
        }

        public Status getStatus() {
            return status;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public static RagIndexResult of(Status status) {
            return new RagIndexResult(status, 0);
        }

        public static RagIndexResult indexed(int chunkCount) {
            return new RagIndexResult(Status.INDEXED, chunkCount);
        }
    }

    /**
     * 批量索引（全量回填用）
     *
     * @param docType 来源类型
     * @param ids     来源主键列表
     * @return 结果统计
     */
    RebuildStat indexBatch(String docType, List<Long> ids);
}
