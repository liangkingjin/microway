package com.wtlx.infrastructure.memory.support;

import com.wtlx.domain.entity.memory.enums.MemoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 记忆归一化单元测试：去重键 / 取值指纹是合并闸门的地基，纯逻辑、无外部依赖。
 */
class MemoryNormalizerTest {

    private final MemoryNormalizer normalizer = new MemoryNormalizer();

    @Test
    void normalizeKeyShouldFoldSeparatorsAndLowercase() {
        assertEquals("budget.level", normalizer.normalizeKey("Budget / LEVEL"));
    }

    @Test
    void normalizeKeyShouldFallbackWhenBlank() {
        assertEquals(MemoryConstants.DEFAULT_MEMORY_KEY, normalizer.normalizeKey("   "));
        assertEquals(MemoryConstants.DEFAULT_MEMORY_KEY, normalizer.normalizeKey(null));
    }

    @Test
    void valueHashShouldIgnoreCaseWhitespaceAndPunctuation() {
        assertEquals(normalizer.valueHash("喜欢徒步"), normalizer.valueHash(" 喜欢徒步！ "));
        assertNotEquals(normalizer.valueHash("喜欢徒步"), normalizer.valueHash("喜欢骑行"));
    }

    @Test
    void dedupKeyShouldMatchEquivalentContentAndDifferOtherwise() {
        String a = normalizer.dedupKey(MemoryType.PREFERENCE, "travel.style", "喜欢徒步");
        String b = normalizer.dedupKey(MemoryType.PREFERENCE, "travel.style", " 喜欢徒步！ ");
        String c = normalizer.dedupKey(MemoryType.PREFERENCE, "travel.style", "喜欢骑行");
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void contentHashShouldTrimBeforeHashing() {
        assertEquals(normalizer.contentHash("abc"), normalizer.contentHash("  abc  "));
    }

    @Test
    void sanitizeSubjectKeyShouldDropNoiseAndNullWhenEmpty() {
        assertEquals("user:123", normalizer.sanitizeSubjectKey("user:12 3"));
        assertNull(normalizer.sanitizeSubjectKey("   "));
        assertNull(normalizer.sanitizeSubjectKey(null));
    }
}
