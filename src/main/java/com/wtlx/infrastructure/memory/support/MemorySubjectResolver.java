package com.wtlx.infrastructure.memory.support;

import com.wtlx.domain.entity.dto.UserDTO;
import com.wtlx.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 记忆主体解析器：把当前登录用户转换为长期记忆的作用域键。
 * <p>
 * <b>唯一来源</b>：{@code UserHolder} 中的登录用户 id。它由 {@code RefreshTokenInterceptor}
 * 在请求入口建立，由 {@code LoginInterceptor} 保证非空——{@code /ai/**} 已纳入登录拦截，
 * 能执行到这里说明请求必然已登录。
 * <p>
 * <b>为什么不再接受 token / clientId 之类的身份线索</b>：多一条线索就多一种"这段请求到底
 * 属于谁"的解释。曾经的后备路径留着两条坑：显式 {@code userId} 参数是直接读写他人记忆的
 * 越权通道；{@code clientId} 会让 token 临时失效的登录用户被静默判成游客，把偏好写进
 * 另一份互不相通的画像里。身份收敛到一处后，主体只有 {@code user:<userId>} 一种形态，
 * 既没有匿名分支，也不存在"这个请求可能没这个人"的歧义。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemorySubjectResolver {

    @Resource
    private MemoryNormalizer memoryNormalizer;

    /**
     * 解析当前登录用户的记忆主体。
     * <p>
     * 必须在请求线程上调用：{@code UserHolder} 是 ThreadLocal，异步线程读不到。
     * 因此调用点固定在 Controller，解析后的主体键经 {@code RunnableConfig} 元数据往下游传递。
     *
     * @return 记忆主体；无登录态时返回 {@link MemoryPrincipal#NONE}
     */
    public MemoryPrincipal resolve() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            log.warn("未取到登录用户，长期记忆主体缺失：本轮不读写长期记忆");
            return MemoryPrincipal.NONE;
        }
        String subjectKey = memoryNormalizer.sanitizeSubjectKey(
                MemoryConstants.SUBJECT_USER_PREFIX + user.getId());
        return subjectKey == null ? MemoryPrincipal.NONE : new MemoryPrincipal(subjectKey);
    }
}
