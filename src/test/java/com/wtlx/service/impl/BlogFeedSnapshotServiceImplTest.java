package com.wtlx.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlogFeedSnapshotServiceImplTest {

    @Test
    void cursorEncodeParseRoundTrip() {
        BlogFeedSnapshotServiceImpl.Cursor cursor =
                new BlogFeedSnapshotServiceImpl.Cursor(1730700000000L, 42L, 30);

        BlogFeedSnapshotServiceImpl.Cursor parsed =
                BlogFeedSnapshotServiceImpl.Cursor.parse(cursor.encode());

        assertEquals(1730700000000L, parsed.ts());
        assertEquals(42L, parsed.id());
        assertEquals(30, parsed.offset());
    }

    @Test
    void invalidCursorFallsBackToFirstPage() {
        assertNull(BlogFeedSnapshotServiceImpl.Cursor.parse(null));
        assertNull(BlogFeedSnapshotServiceImpl.Cursor.parse(""));
        assertNull(BlogFeedSnapshotServiceImpl.Cursor.parse("1730700000000.42"));
        assertNull(BlogFeedSnapshotServiceImpl.Cursor.parse("a.b.c"));
    }

    @Test
    void negativeOffsetIsClamped() {
        BlogFeedSnapshotServiceImpl.Cursor parsed = BlogFeedSnapshotServiceImpl.Cursor.parse("1.2.-5");

        assertEquals(0, parsed.offset());
    }
}
