package com.wtlx.service;

import java.util.List;

/**
 * 游记双列流的分批召回与缓存。
 * <p>
 * 每批固定召回 {@code BATCH_SIZE} 条，批内做一次粗排后写入 Redis ZSet；
 * 后续翻页只读缓存，翻完一批再用批次末尾的时间游标召回下一批。
 * 批次边界由数据库时间游标保证，批内顺序由粗排决定，两者互不影响，因此不会重复。
 */
public interface IBlogFeedSnapshotService {

    /**
     * 取一页游记 id。
     *
     * @param userId   当前用户，未登录传 null
     * @param cursor   翻页游标，为空表示从头开始
     * @param pageSize 期望条数
     */
    FeedPage page(Long userId, String cursor, int pageSize);

    /**
     * @param ids        本页游记 id，已按粗排顺序
     * @param nextCursor 下一页游标，null 表示没有更多
     * @param hasMore    是否还有更多
     */
    record FeedPage(List<Long> ids, String nextCursor, boolean hasMore) {
    }
}
