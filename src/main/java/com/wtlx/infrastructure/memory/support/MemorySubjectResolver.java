package com.wtlx.infrastructure.memory.support;

import cn.hutool.core.bean.BeanUtil;
import com.wtlx.domain.entity.dto.UserDTO;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 记忆主体解析器：把一次请求里的身份线索统一解析为长期记忆的作用域键。
 * <p>
 * <b>为什么需要它</b>：{@code /ai/**} 是免登录路径（EventSource 无法携带 authorization 请求头，
 * 所以 AI 链路整体排除了登录拦截器），这意味着在对话链路里拿不到 {@code UserHolder}。
 * 但长期记忆必须锚定到人，否则跨会话复用无从谈起。因此身份来源按可靠性依次降级：
 * <ol>
 *     <li>{@code userId} 参数 —— 显式指定，管理接口与联调使用；</li>
 *     <li>{@code token} 参数 —— 前端用查询参数透传 token，服务端回查 Redis 登录态拿 userId
 *         （与 {@code RefreshTokenInterceptor} 同源，键 {@code login:token:<token>}）；</li>
 *     <li>{@code clientId} 参数 —— 前端 localStorage 持久化的设备 id，覆盖未登录游客，
 *         保证游客的偏好也能跨会话复用；</li>
 *     <li>全部缺失 —— 返回 {@link MemoryPrincipal#NONE}，本轮只走会话内短期记忆。</li>
 * </ol>
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class MemorySubjectResolver {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MemoryNormalizer memoryNormalizer;

    /**
     * 解析本次请求的记忆主体
     *
     * @param userId   显式用户 id（可选）
     * @param token    登录 token（可选，查询参数透传）
     * @param clientId 匿名客户端 id（可选）
     * @return 记忆主体；无法确定时返回 {@link MemoryPrincipal#NONE}
     */
    public MemoryPrincipal resolve(String userId, String token, String clientId) {
        // 1. 显式 userId 优先级最高
        if (StringUtils.hasText(userId)) {
            return buildUser(userId.trim(), "参数 userId");
        }

        // 2. token → Redis 登录态
        if (StringUtils.hasText(token)) {
            Long resolved = resolveUserIdByToken(token.trim());
            if (resolved != null) {
                return buildUser(String.valueOf(resolved), "token 解析");
            }
        }

        // 3. 兜底：本线程若已由拦截器写入登录态（例如将来 AI 路径恢复鉴权），直接复用
        UserDTO current = UserHolder.getUser();
        if (current != null && current.getId() != null) {
            return buildUser(String.valueOf(current.getId()), "UserHolder");
        }

        // 4. 游客主体
        if (StringUtils.hasText(clientId)) {
            String key = memoryNormalizer.sanitizeSubjectKey(
                    MemoryConstants.SUBJECT_GUEST_PREFIX + clientId.trim());
            if (key != null) {
                return new MemoryPrincipal(key, null, false);
            }
        }

        return MemoryPrincipal.NONE;
    }

    /**
     * 依据 token 回查 Redis 登录态取用户 id
     *
     * @param token 登录 token
     * @return 用户 id；登录态不存在或已过期时返回 null
     */
    private Long resolveUserIdByToken(String token) {
        try {
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                    .entries(RedisConstants.LOGIN_USER_KEY + token);
            if (userMap.isEmpty()) {
                return null;
            }
            UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
            return userDTO.getId();
        } catch (Exception e) {
            log.warn("token 解析用户失败，降级为游客记忆主体: {}", e.getMessage());
            return null;
        }
    }

    private MemoryPrincipal buildUser(String userId, String source) {
        String subjectKey = memoryNormalizer.sanitizeSubjectKey(
                MemoryConstants.SUBJECT_USER_PREFIX + userId);
        if (subjectKey == null) {
            return MemoryPrincipal.NONE;
        }
        try {
            return new MemoryPrincipal(subjectKey, Long.valueOf(userId), true);
        } catch (NumberFormatException e) {
            // userId 非数字（例如外部系统主键）时仍可作为主体键使用，只是没有数值型 userId
            log.debug("userId 非数值，按字符串主体处理: {}（来源：{}）", userId, source);
            return new MemoryPrincipal(subjectKey, null, true);
        }
    }
}
