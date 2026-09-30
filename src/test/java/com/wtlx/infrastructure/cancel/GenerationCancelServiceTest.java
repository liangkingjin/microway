package com.wtlx.infrastructure.cancel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 取消逻辑单元测试（不加载 Spring 上下文，Redis 用 deep stub 兜住）
 */
@ExtendWith(MockitoExtension.class)
class GenerationCancelServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RedissonClient redissonClient;

    @Mock
    private GenerationRegistry registry;

    @InjectMocks
    private GenerationCancelService cancelService;

    @Test
    void cancelShouldReturnCancelledWhenLocalHit() {
        when(registry.cancel("request-0001")).thenReturn(true);
        assertEquals(GenerationCancelService.CancelStatus.CANCELLED, cancelService.cancel("request-0001"));
    }

    @Test
    void cancelShouldForwardWhenLocalMiss() {
        when(registry.cancel("request-0002")).thenReturn(false);
        assertEquals(GenerationCancelService.CancelStatus.FORWARDED, cancelService.cancel("request-0002"));
    }

    @Test
    void cancelShouldRejectIllegalRequestId() {
        assertEquals(GenerationCancelService.CancelStatus.REJECTED, cancelService.cancel("bad:id\r\n"));
        verify(registry, never()).cancel(anyString());
    }

    @Test
    void cancelStaleShouldCancelPreviousStreamOfSameThread() {
        when(registry.findActiveRequestId("thread-0001", "request-new-1")).thenReturn("request-old-1");
        cancelService.cancelStaleByThreadId("thread-0001", "request-new-1");
        verify(registry).cancel("request-old-1");
    }

    @Test
    void cancelStaleShouldDoNothingWhenNoActiveStream() {
        when(registry.findActiveRequestId("thread-0002", "request-new-2")).thenReturn(null);
        cancelService.cancelStaleByThreadId("thread-0002", "request-new-2");
        verify(registry, never()).cancel(anyString());
    }

    @Test
    void cancelLocalShouldOnlyAffectLocalStream() {
        when(registry.cancel("request-0003")).thenReturn(true);
        assertTrue(cancelService.cancelLocal("request-0003"));

        when(registry.cancel("request-0004")).thenReturn(false);
        assertFalse(cancelService.cancelLocal("request-0004"));
    }

    @Test
    void cancelLocalShouldRejectIllegalRequestId() {
        assertFalse(cancelService.cancelLocal("../../etc/passwd"));
        verify(registry, never()).cancel(anyString());
    }

    @Test
    void isMarkedShouldBeFalseWhenFlagAbsent() {
        assertFalse(cancelService.isMarked("request-0005"));
    }

    @Test
    void handleCancelShouldBeIdempotent() {
        GenerationHandle handle = new GenerationHandle("request-0006", "thread-0003");
        int[] runs = {0};
        handle.bindCancelAction(() -> runs[0]++);

        assertTrue(handle.cancel());
        assertFalse(handle.cancel());
        assertEquals(1, runs[0]);
        assertTrue(handle.isCancelled());
    }

    @Test
    void handleShouldRunActionImmediatelyIfCancelledBeforeBinding() {
        GenerationHandle handle = new GenerationHandle("request-0007", "thread-0004");
        int[] runs = {0};
        assertTrue(handle.cancel());
        handle.bindCancelAction(() -> runs[0]++);
        assertEquals(1, runs[0]);
    }

    @Test
    void registryShouldUnregisterAndSupportStaleLookup() {
        GenerationRegistry realRegistry = new GenerationRegistry();
        realRegistry.register(new GenerationHandle("request-a-1", "thread-0009"));

        assertEquals(1, realRegistry.size());
        assertEquals("request-a-1", realRegistry.findActiveRequestId("thread-0009", "request-b-1"));
        assertNull(realRegistry.findActiveRequestId("thread-0009", "request-a-1"));

        realRegistry.unregister("request-a-1");
        assertNull(realRegistry.findActiveRequestId("thread-0009", "request-b-1"));
        assertEquals(0, realRegistry.size());
    }
}
