package com.wtlx.tools.travel;

import com.wtlx.domain.entity.Expenses;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 旅行计划·账单工具：批量记账、单条修改、单条删除。
 * <p>
 * 新增与改动一律走 {@code IExpensesService} 已有的方法，不在这里另写金额汇总——
 * 该服务内部把"新增累加、编辑按差额、删除扣回"收敛在同一个入口，
 * 工具侧另算一份必然会在某次改动后与页面显示对不上。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class ExpenseTool {

    @Resource
    private TravelPlanToolSupport support;

    /**
     * 账单项入参
     */
    @Data
    @NoArgsConstructor
    public static class ExpenseInput {

        @ToolParam(description = "账单标题，例如「往返机票」「两晚民宿」「门票」")
        private String title;

        @ToolParam(description = "金额，单位为元，必须大于 0，最多两位小数")
        private BigDecimal amount;

        @ToolParam(description = "消费日期，格式 yyyy-MM-dd；不传则记为今天")
        private String date;

        @ToolParam(description = "备注，可不填")
        private String remark;
    }

    @Tool(description = "为旅行计划批量添加账单/开销记录。一次调用提交本次要记的全部账目，不要逐条调用。"
            + "每条包含标题与金额，日期不传默认今天。")
    public String addExpenses(
            @ToolParam(description = "旅行计划ID") Integer planId,
            @ToolParam(description = "账单 JSON 数组字符串，"
                    + "例如 [{\"title\":\"往返机票\",\"amount\":1500,\"date\":\"2026-05-01\"}]") String items,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        if (support.ownedPlan(planId, userId) == null) {
            return support.planNotFoundMessage(planId);
        }

        List<ExpenseInput> inputs;
        try {
            inputs = support.parseItems(items, ExpenseInput.class, "账目");
        } catch (IllegalArgumentException e) {
            return "添加失败：" + e.getMessage();
        }
        if (inputs.isEmpty()) {
            return "添加失败：没有提供任何账目。";
        }

        List<String> failures = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int saved = 0;
        for (int i = 0; i < inputs.size(); i++) {
            ExpenseInput input = inputs.get(i);
            String label = "第 " + (i + 1) + " 条";
            if (input == null) {
                failures.add(label + "：内容为空");
                continue;
            }
            if (input.getAmount() == null || input.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                failures.add(label + "：金额必须大于 0");
                continue;
            }
            LocalDate date;
            try {
                date = StringUtils.hasText(input.getDate())
                        ? support.parseDate(input.getDate(), "消费日期")
                        : LocalDate.now();
            } catch (IllegalArgumentException e) {
                failures.add(label + "：" + e.getMessage());
                continue;
            }
            String title = StringUtils.hasText(input.getTitle()) ? input.getTitle().trim() : "未命名支出";
            Expenses expense = new Expenses()
                    .setPlanId(planId)
                    .setTitle(title)
                    .setAmount(input.getAmount())
                    .setDate(date)
                    .setRemark(input.getRemark() == null ? null : input.getRemark().trim());

            // 由服务层负责写入并按金额累加计划总支出，工具不重复实现汇总
            var result = support.expenses().addExpenses(expense);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                saved++;
                total = total.add(input.getAmount());
            } else {
                failures.add(label + "：" + (result.getErrorMsg() == null ? "数据库写入失败" : result.getErrorMsg()));
            }
        }

        String summary = "已为计划ID=" + planId + " 记录 " + saved + " 笔账单，合计 ¥" + support.money(total) + "。";
        if (!failures.isEmpty()) {
            summary += "\n以下账目未写入（已写入的请勿重复提交）：" + String.join("；", failures);
        }
        log.info("AI 批量添加账单, planId: {}, 成功 {} 笔, 合计 {}, 失败 {} 笔", planId, saved, total, failures.size());
        return summary;
    }

    @Tool(description = "修改某一笔账单的标题、金额、日期或备注。只传要改的字段，未传的保持不变；"
            + "金额变化会自动同步计划总支出。修改前先用 getTravelPlanDetail 确认账单ID。")
    public String updateExpense(
            @ToolParam(description = "账单ID") Integer expenseId,
            @ToolParam(description = "新的账单标题，不改则不传", required = false) String title,
            @ToolParam(description = "新的金额，不改则不传", required = false) BigDecimal amount,
            @ToolParam(description = "新的消费日期 yyyy-MM-dd，不改则不传", required = false) String date,
            @ToolParam(description = "新的备注，不改则不传", required = false) String remark,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        Expenses old = support.ownedExpense(expenseId, userId);
        if (old == null) {
            return support.itemNotFoundMessage("账单", expenseId);
        }

        Expenses patch = new Expenses();
        boolean changed = false;
        if (StringUtils.hasText(title)) {
            patch.setTitle(title.trim());
            changed = true;
        }
        if (amount != null) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return "修改失败：金额必须大于 0。";
            }
            patch.setAmount(amount);
            changed = true;
        }
        if (StringUtils.hasText(date)) {
            try {
                patch.setDate(support.parseDate(date, "消费日期"));
            } catch (IllegalArgumentException e) {
                return "修改失败：" + e.getMessage();
            }
            changed = true;
        }
        if (remark != null) {
            patch.setRemark(remark.trim());
            changed = true;
        }
        if (!changed) {
            return "没有需要修改的内容：请至少提供标题、金额、日期或备注中的一项。";
        }

        var result = support.expenses().updateExpenses(expenseId, patch);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return "修改失败：" + (result.getErrorMsg() == null ? "数据库写入异常" : result.getErrorMsg());
        }
        // 回显用"补丁值优先、未传则取原值"，不再多查一次库
        String finalTitle = patch.getTitle() != null ? patch.getTitle() : old.getTitle();
        BigDecimal finalAmount = patch.getAmount() != null ? patch.getAmount() : old.getAmount();
        LocalDate finalDate = patch.getDate() != null ? patch.getDate() : old.getDate();
        log.info("AI 修改账单, userId: {}, expenseId: {}, planId: {}", userId, expenseId, old.getPlanId());
        return "已修改账单（ID=" + expenseId + "）：「" + finalTitle + "」¥"
                + support.money(finalAmount)
                + (finalDate == null ? "" : "，" + finalDate) + "。";
    }

    @Tool(description = "删除某一笔账单，计划总支出会同步扣回该笔金额。"
            + "删除前先用 getTravelPlanDetail 确认账单ID，只删除用户指定的那笔。")
    public String deleteExpense(
            @ToolParam(description = "账单ID") Integer expenseId,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        Expenses old = support.ownedExpense(expenseId, userId);
        if (old == null) {
            return support.itemNotFoundMessage("账单", expenseId);
        }
        var result = support.expenses().deleteExpenses(expenseId);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            return "删除失败：" + (result.getErrorMsg() == null ? "数据库操作异常" : result.getErrorMsg());
        }
        log.info("AI 删除账单, userId: {}, expenseId: {}, planId: {}", userId, expenseId, old.getPlanId());
        return "已删除账单（ID=" + expenseId + "）：「" + old.getTitle() + "」¥"
                + support.money(old.getAmount()) + "，计划总支出已相应扣减。";
    }
}
