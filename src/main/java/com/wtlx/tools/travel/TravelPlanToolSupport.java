package com.wtlx.tools.travel;

import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import com.wtlx.domain.entity.Expenses;
import com.wtlx.domain.entity.ItineraryItems;
import com.wtlx.domain.entity.LuggageItems;
import com.wtlx.domain.entity.TravelPlans;
import com.wtlx.infrastructure.memory.support.MemoryConstants;
import com.wtlx.service.IExpensesService;
import com.wtlx.service.IItineraryItemsService;
import com.wtlx.service.ILuggageItemsService;
import com.wtlx.service.ITravelPlansService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 旅行计划工具集的公共支撑：身份解析、归属校验、日期时间解析、金额汇总。
 * <p>
 * 抽出这一层而不是各工具各写一份，是因为三处判断必须完全一致，否则会出现
 * "A 工具能改、B 工具不能改"的越权缺口：
 * <ol>
 *     <li><b>身份从哪来</b>：工具执行落在 Reactor / 工具线程，{@code UserHolder} 的 ThreadLocal
 *         传不过这层边界，只能从 {@code RunnableConfig} 元数据取（与长期记忆召回工具同一条通道）。
 *         这里不做"取不到就退回 UserHolder"的降级——两个来源语义不同，混用会掩盖上下文传递的断裂。</li>
 *     <li><b>数据归谁</b>：模型给的 planId / itemId 可能来自其它会话或用户，所有写操作前都必须
 *         校验归属，不能因为"模型不会乱填"就跳过。</li>
 *     <li><b>时间怎么解析</b>：模型常写 {@code 9:00} 而非 {@code 09:00}，统一容错，否则整批写入失败。</li>
 * </ol>
 *
 * @author 金小梁
 */
@Component
public class TravelPlanToolSupport {

    /**
     * 计划状态缺省值，与前端创建计划时的取值保持一致
     */
    static final String DEFAULT_PLAN_STATUS = "待出发";

    /**
     * 时间解析格式：{@code H} 与 {@code m} 都是可变宽度，{@code 9:00} / {@code 09:00} / {@code 9:5}
     * 都能解析——模型给出的时间写法不可控，宽进严出更省一次失败重试。
     */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:m");

    @Resource
    private ITravelPlansService travelPlansService;

    @Resource
    private IItineraryItemsService itineraryItemsService;

    @Resource
    private IExpensesService expensesService;

    @Resource
    private ILuggageItemsService luggageItemsService;

    public ITravelPlansService plans() {
        return travelPlansService;
    }

    public IItineraryItemsService itineraries() {
        return itineraryItemsService;
    }

    public IExpensesService expenses() {
        return expensesService;
    }

    public ILuggageItemsService luggage() {
        return luggageItemsService;
    }

    /**
     * 从工具上下文取当前登录用户 id。
     *
     * @param toolContext 框架注入的工具上下文
     * @return 用户 id；取不到时返回 null，由各工具返回明确的失败说明
     */
    public Long resolveUserId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        return ToolContextHelper.getConfig(toolContext)
                .flatMap(config -> config.metadata(MemoryConstants.USER_ID_METADATA))
                .filter(Long.class::isInstance)
                .map(Long.class::cast)
                .orElse(null);
    }

    /**
     * 未登录时的统一提示：明确告诉模型"不是数据不存在"，而是做不到
     */
    public String noUserMessage() {
        return "当前会话未关联到登录用户，无法读写旅行计划。请提示用户先登录后再试。";
    }

    /**
     * 取属于该用户的计划；不存在与不属于该用户返回同一个 null，
     * 避免通过错误文案区分出"计划存在但归别人"，泄露他人数据的存在性
     */
    public TravelPlans ownedPlan(Integer planId, Long userId) {
        if (planId == null || userId == null) {
            return null;
        }
        TravelPlans plan = travelPlansService.getById(planId);
        if (plan == null || plan.getUserId() == null) {
            return null;
        }
        return userId.equals(plan.getUserId().longValue()) ? plan : null;
    }

    public String planNotFoundMessage(Integer planId) {
        return "未找到旅行计划（ID=" + planId + "），或该计划不属于当前用户。"
                + "请先调用 listTravelPlans 确认计划 ID，不要凭空猜测。";
    }

    /**
     * 取属于该用户的行程条目，并顺带校验其所属计划
     */
    public ItineraryItems ownedItinerary(Integer itemId, Long userId) {
        ItineraryItems item = itineraryItemsService.getById(itemId);
        return item != null && ownedPlan(item.getPlanId(), userId) != null ? item : null;
    }

    public Expenses ownedExpense(Integer expenseId, Long userId) {
        Expenses expense = expensesService.getById(expenseId);
        return expense != null && ownedPlan(expense.getPlanId(), userId) != null ? expense : null;
    }

    public LuggageItems ownedLuggage(Integer itemId, Long userId) {
        LuggageItems item = luggageItemsService.getById(itemId);
        return item != null && ownedPlan(item.getPlanId(), userId) != null ? item : null;
    }

    public String itemNotFoundMessage(String what, Integer itemId) {
        return "未找到" + what + "（ID=" + itemId + "），或它不属于当前用户。"
                + "请先调用 getTravelPlanDetail 查看真实 ID。";
    }

    /**
     * 解析 yyyy-MM-dd 日期
     *
     * @throws IllegalArgumentException 格式不合法，由调用方转成可读提示
     */
    public LocalDate parseDate(String text, String fieldName) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "格式应为 yyyy-MM-dd，例如 2026-05-01，收到：" + text);
        }
    }

    /**
     * 解析时间：模型既会写 {@code 9:00} 也会写 {@code 09:00}，pattern 用 {@code H} 两种都能解析
     *
     * @throws IllegalArgumentException 格式不合法
     */
    public LocalTime parseTime(String text, String fieldName) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return LocalTime.parse(text.trim(), TIME_FORMAT);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + "格式应为 HH:mm，例如 09:00，收到：" + text);
        }
    }

    /**
     * 行程时长文案，与前端创建计划时的口径一致（首尾都算天数，晚数 = 天数 - 1）
     */
    public String durationOf(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long nights = days - 1;
        return days + "天" + nights + "晚";
    }

    /**
     * 计划总支出：以账单流水求和为准，不信任表里的 total_amount 汇总值，
     * 避免历史脏数据让模型转述出错误金额
     */
    public BigDecimal sumAmount(List<Expenses> expensesList) {
        return expensesList.stream()
                .map(expense -> expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 金额展示口径：两位小数，供各工具统一格式化
     */
    public String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
