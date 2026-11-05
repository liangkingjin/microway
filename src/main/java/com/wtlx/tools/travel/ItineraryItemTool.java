package com.wtlx.tools.travel;

import com.wtlx.domain.entity.ItineraryItems;
import com.wtlx.service.IScenicInfoService;
import com.wtlx.service.impl.ScenicInfoServiceImpl;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 旅行计划·行程工具：按天逐条安排行程，支持批量新增、单条修改、单条删除。
 * <p>
 * <b>为什么新增是批量、而不是逐条</b>：一份 5 天的行程通常有 10~20 个条目。若开放"单条新增"，
 * 模型就要连续调用十几次工具——每一步都是一轮完整的模型推理，既慢又容易在中途把日期、措辞写偏，
 * 而 {@code ModelCallLimitHook} 只给了 20 步。所以这里只提供批量入口：一次提交整段日程。
 * <p>
 * 修改与删除仍是单条（用 ID），因为这类操作是"针对某一条"的，本就不存在批量语义。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class ItineraryItemTool {

    @Resource
    private TravelPlanToolSupport support;

    @Resource
    private IScenicInfoService scenicInfoService;

    /** 行程类型白名单，与前端标签映射保持一致 */
    private static final java.util.Set<String> ITEM_TYPES =
            java.util.Set.of("SCENIC", "TRANSPORT", "HOTEL", "DINING", "OTHER");

    /**
     * 行程条目入参
     */
    @Data
    @NoArgsConstructor
    public static class ItineraryItemInput {

        @ToolParam(description = "行程日期，格式 yyyy-MM-dd，必须落在计划起止日期范围内")
        private String date;

        @ToolParam(description = "行程时间，24 小时制 HH:mm，例如 09:00")
        private String time;

        @ToolParam(description = "行程内容，例如「抵达昆明长水机场，打车入住酒店」")
        private String content;

        @ToolParam(required = false, description = "关联景点名称：当这条行程是去某个具体景点时填写"
                + "（如「黄鹤楼」「湖北省博物馆」），必须是百度地图能检索到的真实地点名；"
                + "纯交通、用餐、休息、自由活动的条目不填。填了之后用户可以在行程里点开该景点的详情。")
        private String spotName;

        @ToolParam(required = false, description = "行程类型，可选值：SCENIC（景点游览）、"
                + "TRANSPORT（交通赶路）、HOTEL（入住退房住宿）、DINING（用餐）、OTHER（其他自由活动）。"
                + "用于行程分类展示与地图点位着色，务必按实际含义填写。")
        private String itemType;
    }

    @Tool(description = "为旅行计划批量添加行程条目。一次调用提交该计划的所有日程，不要逐条调用。"
            + "每个条目包含日期、时间和内容；重复调用会追加而不是覆盖，补录漏掉的日程时才再次调用。")
    public String addItineraryItems(
            @ToolParam(description = "旅行计划ID") Integer planId,
            @ToolParam(description = "行程条目 JSON 数组字符串，按时间顺序排列，"
                    + "例如 [{\"date\":\"2026-05-01\",\"time\":\"09:00\",\"content\":\"抵达昆明\"}]") String items,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        if (support.ownedPlan(planId, userId) == null) {
            return support.planNotFoundMessage(planId);
        }

        List<ItineraryItemInput> inputs;
        try {
            inputs = support.parseItems(items, ItineraryItemInput.class, "行程条目");
        } catch (IllegalArgumentException e) {
            return "添加失败：" + e.getMessage();
        }
        if (inputs.isEmpty()) {
            return "添加失败：没有提供任何行程条目。";
        }

        List<String> failures = new ArrayList<>();
        List<String> spotNames = new ArrayList<>();
        int saved = 0;
        for (int i = 0; i < inputs.size(); i++) {
            ItineraryItemInput input = inputs.get(i);
            String label = "第 " + (i + 1) + " 条";
            if (input == null || !StringUtils.hasText(input.getContent())) {
                failures.add(label + "：行程内容为空");
                continue;
            }
            LocalDate date;
            LocalTime time;
            try {
                date = support.parseDate(input.getDate(), "行程日期");
                time = support.parseTime(input.getTime(), "行程时间");
            } catch (IllegalArgumentException e) {
                failures.add(label + "：" + e.getMessage());
                continue;
            }
            String spotName = resolveSpotName(input);
            String content = normalizeContent(input.getContent().trim(), spotName);
            ItineraryItems item = new ItineraryItems()
                    .setPlanId(planId)
                    .setDate(date)
                    .setTime(time)
                    .setContent(content)
                    .setSpotName(spotName)
                    .setItemType(resolveItemType(input, spotName));
            if (support.itineraries().save(item)) {
                saved++;
                // 顺带把这条行程里的【地点】收集起来：计划一生成就把景点资料准备好，
                // 用户点开详情时直接命中缓存，而不是现场等生成
                for (String spot : ScenicInfoServiceImpl.extractSpotNames(content)) {
                    if (!spotNames.contains(spot)) {
                        spotNames.add(spot);
                    }
                }
                if (spotName != null && !spotNames.contains(spotName)) {
                    spotNames.add(spotName);
                }
            } else {
                failures.add(label + "：数据库写入失败");
            }
        }

        if (!spotNames.isEmpty()) {
            scenicInfoService.pregenerate(spotNames);
        }

        String summary = "已为计划ID=" + planId + " 添加 " + saved + " 条行程。";
        if (!failures.isEmpty()) {
            summary += "\n以下条目未写入（已写入的请勿重复提交）：" + String.join("；", failures);
        }
        log.info("AI 批量添加行程, planId: {}, 成功 {} 条, 失败 {} 条", planId, saved, failures.size());
        return summary;
    }

    /**
     * 景点名优先取显式入参（AI 按工具描述填写），其次从 content 的【】标注里解析，
     * 兼容模型只写正文、没传 spotName 的情况。
     */
    private String resolveSpotName(ItineraryItemInput input) {
        String explicit = input.getSpotName() == null ? "" : input.getSpotName().strip();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        List<String> fromContent = ScenicInfoServiceImpl.extractSpotNames(input.getContent());
        return fromContent.isEmpty() ? null : fromContent.get(0);
    }

    /** 有景点但正文没标注时补上【景点名】前缀——前端只认这一种格式，避免两套渲染逻辑 */
    private String normalizeContent(String content, String spotName) {
        if (spotName == null || content.startsWith("【")) {
            return content;
        }
        return "【" + spotName + "】" + content;
    }

    /**
     * 行程类型白名单；没传或传了不认识的值时按"有没有关联景点"兜底推断，
     * 保证老数据 / 模型不填的情况下列表照样能分类展示。
     */
    private String resolveItemType(ItineraryItemInput input, String spotName) {
        String raw = input.getItemType() == null ? "" : input.getItemType().strip().toUpperCase();
        if (ITEM_TYPES.contains(raw)) {
            return raw;
        }
        return spotName == null ? "OTHER" : "SCENIC";
    }

    @Tool(description = "修改某一条行程的日期、时间或内容。只传要改的字段，未传的保持不变。"
            + "修改前先用 getTravelPlanDetail 确认条目ID。")
    public String updateItineraryItem(
            @ToolParam(description = "行程条目ID") Integer itemId,
            @ToolParam(description = "新的日期 yyyy-MM-dd，不改则不传", required = false) String date,
            @ToolParam(description = "新的时间 HH:mm，不改则不传", required = false) String time,
            @ToolParam(description = "新的行程内容，不改则不传", required = false) String content,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        ItineraryItems item = support.ownedItinerary(itemId, userId);
        if (item == null) {
            return support.itemNotFoundMessage("行程条目", itemId);
        }

        boolean changed = false;
        if (StringUtils.hasText(date)) {
            try {
                item.setDate(support.parseDate(date, "行程日期"));
            } catch (IllegalArgumentException e) {
                return "修改失败：" + e.getMessage();
            }
            changed = true;
        }
        if (StringUtils.hasText(time)) {
            try {
                item.setTime(support.parseTime(time, "行程时间"));
            } catch (IllegalArgumentException e) {
                return "修改失败：" + e.getMessage();
            }
            changed = true;
        }
        if (StringUtils.hasText(content)) {
            item.setContent(content.trim());
            changed = true;
        }
        if (!changed) {
            return "没有需要修改的内容：请至少提供日期、时间或内容中的一项。";
        }
        if (!support.itineraries().updateById(item)) {
            return "修改失败：数据库写入异常，请稍后重试。";
        }
        log.info("AI 修改行程, userId: {}, itemId: {}, planId: {}", userId, itemId, item.getPlanId());
        return "已修改行程（ID=" + itemId + "）："
                + (item.getDate() == null ? "" : item.getDate() + " ")
                + (item.getTime() == null ? "" : item.getTime() + " ")
                + item.getContent() + "。";
    }

    @Tool(description = "删除某一条行程。删除前先用 getTravelPlanDetail 确认条目ID，仅删除用户指定的那一条。")
    public String deleteItineraryItem(
            @ToolParam(description = "行程条目ID") Integer itemId,
            ToolContext toolContext) {

        Long userId = support.resolveUserId(toolContext);
        if (userId == null) {
            return support.noUserMessage();
        }
        ItineraryItems item = support.ownedItinerary(itemId, userId);
        if (item == null) {
            return support.itemNotFoundMessage("行程条目", itemId);
        }
        if (!support.itineraries().removeById(itemId)) {
            return "删除失败：数据库操作异常，请稍后重试。";
        }
        log.info("AI 删除行程, userId: {}, itemId: {}, planId: {}", userId, itemId, item.getPlanId());
        return "已删除行程（ID=" + itemId + "）：" + item.getContent() + "。";
    }
}
