package com.wtlx.tools.travel;

import com.wtlx.domain.entity.Expenses;
import com.wtlx.domain.entity.ItineraryItems;
import com.wtlx.domain.entity.LuggageItems;
import com.wtlx.domain.entity.TravelPlans;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 旅行计划主档工具：创建、列出、查看详情、修改、删除。
 * <p>
 * 之所以把"查看详情"做成<b>一个</b>工具而不是计划/行程/账单/行李各查一次，是因为模型每多一次
 * 工具调用就多一轮模型往返：既慢，也更容易在中途跑偏。一次拿到全貌，后续改动才能对准真实 ID。
 * <p>
 * 写操作的边界：只有用户明确表达了"存下来 / 建计划 / 改一下 / 删掉"才执行；
 * 仅仅是咨询建议时不允许写库（该约束写在系统提示词里，工具侧只做数据与权限校验）。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class TravelPlanTool {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private TravelPlanToolSupport support;

    @Tool(description = "创建一份新的旅行计划（空计划，不含日程与清单）。"
            + "当用户明确要求「帮我建一个旅行计划」「把这些安排存下来」「生成一个行程计划」时调用。"
            + "创建成功会返回计划ID，后续添加行程、账单、行李都必须用这个ID。")
    public String createTravelPlan(
            @ToolParam(description = "计划名称，例如「云南5日游」「成都周末行」") String title,
            @ToolParam(description = "出发日期，格式 yyyy-MM-dd，例如 2026-05-01") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，不能早于出发日期") String endDate,
            @ToolParam(description = "计划状态，建议取值：待出发 / 进行中 / 已完成，不传默认待出发", required = false) String status,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        if (!StringUtils.hasText(title)) {
            return "创建失败：计划名称不能为空。";
        }

        LocalDate start;
        LocalDate end;
        try {
            start = support.parseDate(startDate, "出发日期");
            end = support.parseDate(endDate, "结束日期");
        } catch (IllegalArgumentException e) {
            return "创建失败：" + e.getMessage();
        }
        if (end.isBefore(start)) {
            return "创建失败：结束日期不能早于出发日期（出发 " + start + "，结束 " + end + "）。";
        }

        TravelPlans plan = new TravelPlans()
                .setUserId(Math.toIntExact(userId))
                .setTitle(title.trim())
                .setStartDate(start)
                .setEndDate(end)
                .setDuration(support.durationOf(start, end))
                .setStatus(StringUtils.hasText(status) ? status.trim() : TravelPlanToolSupport.DEFAULT_PLAN_STATUS);

        if (!support.plans().save(plan)) {
            return "创建失败：数据库写入异常，请稍后重试。";
        }
        log.info("AI 创建旅行计划成功, userId: {}, planId: {}, title: {}", userId, plan.getId(), plan.getTitle());
        return "已创建旅行计划「" + plan.getTitle() + "」，计划ID=" + plan.getId()
                + "，日期 " + start.format(DATE_FMT) + " 至 " + end.format(DATE_FMT)
                + "（" + plan.getDuration() + "），状态：" + plan.getStatus()
                + "。接下来可用该计划ID添加行程、账单和行李。";
    }

    @Tool(description = "列出当前用户的全部旅行计划，含计划ID、名称、日期、天数、状态与总支出。"
            + "用户问「我有哪些旅行计划」、或需要确认某个计划ID时调用。")
    public String listTravelPlans(ToolContext toolContext) {
        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }

        List<TravelPlans> plans = support.plans().query()
                .eq("user_id", userId)
                .orderByDesc("id")
                .list();
        if (plans.isEmpty()) {
            return "当前用户还没有任何旅行计划。";
        }

        StringBuilder sb = new StringBuilder("共 " + plans.size() + " 份旅行计划：\n");
        for (TravelPlans plan : plans) {
            sb.append("- ID=").append(plan.getId())
                    .append("｜").append(plan.getTitle())
                    .append("｜").append(plan.getStartDate() == null ? "未定" : plan.getStartDate().format(DATE_FMT))
                    .append(" 至 ").append(plan.getEndDate() == null ? "未定" : plan.getEndDate().format(DATE_FMT))
                    .append("｜").append(plan.getDuration() == null ? "未计算" : plan.getDuration())
                    .append("｜").append(plan.getStatus() == null ? "未设置" : plan.getStatus())
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询一份旅行计划的完整详情：基本信息、按日期分组的全部行程、账单流水、行李清单，"
            + "并给出每个条目的真实ID。修改或删除任何条目之前都要先调用本工具确认ID，不要凭空猜测。")
    public String getTravelPlanDetail(
            @ToolParam(description = "旅行计划ID") Integer planId,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        TravelPlans plan = support.ownedPlan(planId, userId);
        if (plan == null) {
            return support.planNotFoundMessage(planId);
        }

        List<ItineraryItems> itineraries = support.itineraries().query()
                .eq("plan_id", planId)
                .orderByAsc("date", "time")
                .list();
        List<Expenses> expenses = support.expenses().query()
                .eq("plan_id", planId)
                .orderByAsc("date", "id")
                .list();
        List<LuggageItems> luggage = support.luggage().query()
                .eq("plan_id", planId)
                .orderByAsc("id")
                .list();

        StringBuilder sb = new StringBuilder();
        sb.append("【计划】").append(plan.getTitle()).append("（ID=").append(plan.getId()).append("）\n")
                .append("日期：").append(plan.getStartDate() == null ? "未定" : plan.getStartDate().format(DATE_FMT))
                .append(" 至 ").append(plan.getEndDate() == null ? "未定" : plan.getEndDate().format(DATE_FMT))
                .append("（").append(plan.getDuration() == null ? "未计算" : plan.getDuration()).append("）")
                .append("｜状态：").append(plan.getStatus() == null ? "未设置" : plan.getStatus())
                .append("\n");

        sb.append("\n【行程】");
        if (itineraries.isEmpty()) {
            sb.append("暂无\n");
        } else {
            sb.append("共 ").append(itineraries.size()).append(" 项\n");
            // 按日分组输出，模型转述时不必自己再做聚合
            Map<LocalDate, List<ItineraryItems>> byDate = itineraries.stream()
                    .filter(item -> item.getDate() != null)
                    .collect(Collectors.groupingBy(ItineraryItems::getDate, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<LocalDate, List<ItineraryItems>> entry : byDate.entrySet()) {
                sb.append("- ").append(entry.getKey().format(DATE_FMT)).append("：");
                sb.append(entry.getValue().stream()
                        .map(item -> (item.getTime() == null ? "时间未填" : item.getTime().toString())
                                + " " + item.getContent() + "（ID=" + item.getId() + "）")
                        .collect(Collectors.joining("｜")));
                sb.append("\n");
            }
        }

        sb.append("\n【账单】");
        if (expenses.isEmpty()) {
            sb.append("暂无\n");
        } else {
            sb.append("共 ").append(expenses.size()).append(" 笔，合计 ¥")
                    .append(support.sumAmount(expenses).setScale(2, java.math.RoundingMode.HALF_UP)).append("\n");
            for (Expenses expense : expenses) {
                sb.append("- ID=").append(expense.getId()).append("｜")
                        .append(expense.getTitle()).append("｜¥").append(expense.getAmount())
                        .append("｜").append(expense.getDate() == null ? "日期未填" : expense.getDate().format(DATE_FMT));
                if (StringUtils.hasText(expense.getRemark())) {
                    sb.append("｜备注：").append(expense.getRemark());
                }
                sb.append("\n");
            }
        }

        sb.append("\n【行李】");
        if (luggage.isEmpty()) {
            sb.append("暂无\n");
        } else {
            sb.append("共 ").append(luggage.size()).append(" 件\n");
            for (LuggageItems item : luggage) {
                sb.append("- ID=").append(item.getId()).append("｜").append(item.getName())
                        .append("｜×").append(item.getQuantity() == null ? 1 : item.getQuantity())
                        .append("｜").append(Boolean.TRUE.equals(item.getIsPacked()) ? "已打包" : "未打包")
                        .append("\n");
            }
        }
        return sb.toString();
    }

    @Tool(description = "修改旅行计划的基本信息（名称、起止日期、状态）。只传需要修改的字段，未传的保持不变；"
            + "日期变更后会自动重算天数。改日期前应先确认计划ID。")
    public String updateTravelPlan(
            @ToolParam(description = "旅行计划ID") Integer planId,
            @ToolParam(description = "新的计划名称，不改则不传", required = false) String title,
            @ToolParam(description = "新的出发日期 yyyy-MM-dd，不改则不传", required = false) String startDate,
            @ToolParam(description = "新的结束日期 yyyy-MM-dd，不改则不传", required = false) String endDate,
            @ToolParam(description = "新的状态：待出发 / 进行中 / 已完成，不改则不传", required = false) String status,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        TravelPlans plan = support.ownedPlan(planId, userId);
        if (plan == null) {
            return support.planNotFoundMessage(planId);
        }

        boolean changed = false;
        List<String> changes = new ArrayList<>();

        if (StringUtils.hasText(title)) {
            plan.setTitle(title.trim());
            changes.add("名称改为「" + plan.getTitle() + "」");
            changed = true;
        }
        if (StringUtils.hasText(startDate) || StringUtils.hasText(endDate)) {
            LocalDate start;
            LocalDate end;
            try {
                start = StringUtils.hasText(startDate) ? support.parseDate(startDate, "出发日期") : plan.getStartDate();
                end = StringUtils.hasText(endDate) ? support.parseDate(endDate, "结束日期") : plan.getEndDate();
            } catch (IllegalArgumentException e) {
                return "修改失败：" + e.getMessage();
            }
            if (start == null || end == null) {
                return "修改失败：计划的起止日期不完整，请同时给出出发与结束日期。";
            }
            if (end.isBefore(start)) {
                return "修改失败：结束日期不能早于出发日期（出发 " + start + "，结束 " + end + "）。";
            }
            plan.setStartDate(start).setEndDate(end).setDuration(support.durationOf(start, end));
            changes.add("日期改为 " + start.format(DATE_FMT) + " 至 " + end.format(DATE_FMT)
                    + "（" + plan.getDuration() + "）");
            changed = true;
        }
        if (StringUtils.hasText(status)) {
            plan.setStatus(status.trim());
            changes.add("状态改为「" + plan.getStatus() + "」");
            changed = true;
        }

        if (!changed) {
            return "没有需要修改的内容：请至少提供一个要改的字段（名称/出发日期/结束日期/状态）。";
        }
        if (!support.plans().updateById(plan)) {
            return "修改失败：数据库写入异常，请稍后重试。";
        }
        log.info("AI 修改旅行计划, userId: {}, planId: {}, changes: {}", userId, planId, changes);
        return "已修改旅行计划「" + plan.getTitle() + "」（ID=" + plan.getId() + "）："
                + String.join("；", changes) + "。";
    }

    @Tool(description = "删除整份旅行计划，连同它的全部行程、账单与行李一起删除。"
            + "仅在用户明确要求删除某份计划时调用；删除不可恢复，调用前必须先确认计划ID与名称。")
    public String deleteTravelPlan(
            @ToolParam(description = "旅行计划ID") Integer planId,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        TravelPlans plan = support.ownedPlan(planId, userId);
        if (plan == null) {
            return support.planNotFoundMessage(planId);
        }

        // 复用现有级联删除（计划 + 行程 + 账单 + 行李），避免工具侧再写一份删除逻辑
        support.plans().deleteTravelPlans(planId);
        log.info("AI 删除旅行计划, userId: {}, planId: {}, title: {}", userId, planId, plan.getTitle());
        return "已删除旅行计划「" + plan.getTitle() + "」（ID=" + planId + "）及其全部行程、账单和行李。";
    }

    /**
     * 供其它工具复用的日期格式器，避免各处重复构造
     */
    static DateTimeFormatter dateFormatter() {
        return DATE_FMT;
    }

    /**
     * 账单金额的展示口径：两位小数
     */
    static String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 行程排序：日期 + 时间，时间缺失的排最后（模型补录时常见）
     */
    static Comparator<ItineraryItems> itineraryOrder() {
        return Comparator.comparing(ItineraryItems::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ItineraryItems::getTime, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
