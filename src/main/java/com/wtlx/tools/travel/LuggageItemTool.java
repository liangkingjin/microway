package com.wtlx.tools.travel;

import com.wtlx.domain.entity.LuggageItems;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 旅行计划·行李清单工具：批量添加、单条修改（含打包状态）、单条删除。
 * <p>
 * 打包状态是清单里唯一会随出行推进而变化的状态，所以修改接口把它做成可单独更新的字段，
 * 便于"把充电宝标记为已打包"这类单点操作，而不用重传名称与数量。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class LuggageItemTool {

    private static final int MAX_QUANTITY = 999;

    @Resource
    private TravelPlanToolSupport support;

    /**
     * 行李条目入参
     */
    @Data
    @NoArgsConstructor
    public static class LuggageItemInput {

        @ToolParam(description = "物品名称，例如「充电宝」「登山鞋」")
        private String name;

        @ToolParam(description = "数量，默认 1")
        private Integer quantity;

        @ToolParam(description = "是否已打包，默认 false（未打包）")
        private Boolean isPacked;
    }

    @Tool(description = "为旅行计划批量添加行李清单物品。一次调用提交全部物品，不要逐条调用。"
            + "每项包含名称、数量与是否已打包。")
    public String addLuggageItems(
            @ToolParam(description = "旅行计划ID") Integer planId,
            @ToolParam(description = "行李物品数组") List<LuggageItemInput> items,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        if (support.ownedPlan(planId, userId) == null) {
            return support.planNotFoundMessage(planId);
        }
        if (items == null || items.isEmpty()) {
            return "添加失败：没有提供任何物品。";
        }

        List<String> failures = new ArrayList<>();
        int saved = 0;
        for (int i = 0; i < items.size(); i++) {
            LuggageItemInput input = items.get(i);
            String label = "第 " + (i + 1) + " 条";
            if (input == null || !StringUtils.hasText(input.getName())) {
                failures.add(label + "：物品名称为空");
                continue;
            }
            int quantity = input.getQuantity() == null ? 1 : input.getQuantity();
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                failures.add(label + "：数量应在 1~" + MAX_QUANTITY + " 之间，收到 " + quantity);
                continue;
            }
            LuggageItems item = new LuggageItems()
                    .setPlanId(planId)
                    .setName(input.getName().trim())
                    .setQuantity(quantity)
                    .setIsPacked(Boolean.TRUE.equals(input.getIsPacked()));
            if (support.luggage().save(item)) {
                saved++;
            } else {
                failures.add(label + "：数据库写入失败");
            }
        }

        String summary = "已为计划ID=" + planId + " 添加 " + saved + " 件行李物品。";
        if (!failures.isEmpty()) {
            summary += "\n以下物品未写入（已写入的请勿重复提交）：" + String.join("；", failures);
        }
        log.info("AI 批量添加行李, planId: {}, 成功 {} 件, 失败 {} 件", planId, saved, failures.size());
        return summary;
    }

    @Tool(description = "修改某件行李的名称、数量或打包状态。只传要改的字段，未传的保持不变；"
            + "把某件物品标记为已打包时，只传 isPacked=true 即可。修改前先用 getTravelPlanDetail 确认物品ID。")
    public String updateLuggageItem(
            @ToolParam(description = "行李物品ID") Integer itemId,
            @ToolParam(description = "新的物品名称，不改则不传", required = false) String name,
            @ToolParam(description = "新的数量，不改则不传", required = false) Integer quantity,
            @ToolParam(description = "是否已打包：true 已打包 / false 未打包，不改则不传", required = false) Boolean isPacked,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        LuggageItems item = support.ownedLuggage(itemId, userId);
        if (item == null) {
            return support.itemNotFoundMessage("行李物品", itemId);
        }

        boolean changed = false;
        if (StringUtils.hasText(name)) {
            item.setName(name.trim());
            changed = true;
        }
        if (quantity != null) {
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                return "修改失败：数量应在 1~" + MAX_QUANTITY + " 之间。";
            }
            item.setQuantity(quantity);
            changed = true;
        }
        if (isPacked != null) {
            item.setIsPacked(isPacked);
            changed = true;
        }
        if (!changed) {
            return "没有需要修改的内容：请至少提供名称、数量或打包状态中的一项。";
        }
        if (!support.luggage().updateById(item)) {
            return "修改失败：数据库写入异常，请稍后重试。";
        }
        log.info("AI 修改行李, userId: {}, itemId: {}, planId: {}", userId, itemId, item.getPlanId());
        return "已修改行李（ID=" + itemId + "）：" + item.getName()
                + " ×" + (item.getQuantity() == null ? 1 : item.getQuantity())
                + "，" + (Boolean.TRUE.equals(item.getIsPacked()) ? "已打包" : "未打包") + "。";
    }

    @Tool(description = "删除某一件行李物品。删除前先用 getTravelPlanDetail 确认物品ID，只删除用户指定的那一件。")
    public String deleteLuggageItem(
            @ToolParam(description = "行李物品ID") Integer itemId,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        LuggageItems item = support.ownedLuggage(itemId, userId);
        if (item == null) {
            return support.itemNotFoundMessage("行李物品", itemId);
        }
        if (!support.luggage().removeById(itemId)) {
            return "删除失败：数据库操作异常，请稍后重试。";
        }
        log.info("AI 删除行李, userId: {}, itemId: {}, planId: {}", userId, itemId, item.getPlanId());
        return "已删除行李（ID=" + itemId + "）：" + item.getName() + "。";
    }
}
