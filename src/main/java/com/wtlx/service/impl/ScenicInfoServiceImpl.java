package com.wtlx.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtlx.domain.entity.ScenicSpots;
import com.wtlx.domain.entity.dto.ai.ScenicInfoVO;
import com.wtlx.infrastructure.memory.support.LlmCalls;
import com.wtlx.infrastructure.rag.retriever.RagHit;
import com.wtlx.infrastructure.rag.retriever.RagSearchService;
import com.wtlx.service.IScenicInfoService;
import com.wtlx.service.IScenicSpotsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 景点 AI 详情：<b>文案取自站内知识</b>，<b>图片取自站内素材</b>，都不靠凭空生成。
 * <p>
 * 数据来源：<br>
 * 1) 介绍 / 游览建议：先经 {@link RagSearchService} 检索站内已发布的游记与攻略，
 * 把命中切片交给大模型<b>据此归纳</b>（无命中时才退回通识介绍，并在文案里注明来源有限）；<br>
 * 2) 图片：先查业务库 {@code scenic_spots} 的官方配图，再取 RAG 命中文档的封面，
 * 去重后最多 3 张；一张都没有时前端用占位样式兜底。
 * <p>
 * 生成时机：AI 生成行程、写入行程条目后即触发预生成（见 ItineraryItemTool），
 * 用户点开景点详情时多数已命中缓存。首次生成耗时（检索 + 模型）在秒级到十几秒，
 * 接口用 PENDING + 前端轮询的方式返回，不阻塞请求线程。
 * <p>
 * 并发控制：同一景点单飞；连续失败 3 次后冷却 10 分钟，避免前端轮询把生成打爆。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class ScenicInfoServiceImpl implements IScenicInfoService {

    /** 行程条目里 AI 标注的地点名，如「【黄鹤楼】登楼远眺」 */
    private static final Pattern POI_PATTERN = Pattern.compile("【([^】]{1,40})】");

    private static final int MAX_IMAGES = 3;
    private static final int RAG_TOP_K = 6;
    private static final double RAG_THRESHOLD = 0.45D;
    private static final int CONTEXT_MAX_CHARS = 2600;
    private static final int FAIL_THRESHOLD = 3;
    private static final long FAIL_COOLDOWN_MS = 10 * 60 * 1000L;

    private static final String FALLBACK_INTRO =
            "站内暂时没有这个地点的详细资料。你可以先在地图上查看它的位置，以及它在整条行程动线里的前后衔接。";

    @Qualifier("openAiChatModel")
    @Resource
    private ChatModel chatModel;

    @Resource
    private IScenicSpotsService scenicSpotsService;

    @Resource
    private RagSearchService ragSearchService;

    @Resource(name = "aiJdbcTemplate")
    private JdbcTemplate aiJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 进行中的生成任务，key 为 spot_key；单飞防并发重复生成 */
    private final ConcurrentHashMap<String, CompletableFuture<Void>> inflight = new ConcurrentHashMap<>();

    /** 失败统计 [次数, 最近一次时间戳]，冷却用 */
    private final ConcurrentHashMap<String, long[]> failures = new ConcurrentHashMap<>();

    @Override
    public ScenicInfoVO getOrGenerate(String name) {
        String key = name == null ? "" : name.strip();
        if (key.isEmpty() || key.length() > 60) {
            return ScenicInfoVO.builder().status("FAILED").name(key)
                    .intro("景点名称不合法").tips(List.of()).images(List.of()).build();
        }

        ScenicInfoVO cached = queryCache(key);
        if (cached != null) {
            return cached;
        }

        long[] stat = failures.get(key);
        if (stat != null && stat[0] >= FAIL_THRESHOLD
                && System.currentTimeMillis() - stat[1] < FAIL_COOLDOWN_MS) {
            return ScenicInfoVO.builder().status("FAILED").name(key)
                    .intro("该景点资料暂时生成失败，请稍后再试").tips(List.of()).images(List.of()).build();
        }
        failures.remove(key);

        triggerGenerate(key);
        return ScenicInfoVO.builder().status("PENDING").name(key).tips(List.of()).images(List.of()).build();
    }

    @Override
    public void pregenerate(List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        names.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::strip)
                .distinct()
                .forEach(name -> {
                    // 已有缓存就不再打扰：预生成只是把首次打开详情页的等待挪到后台
                    if (queryCache(name) == null) {
                        triggerGenerate(name);
                    }
                });
    }

    @Override
    public boolean evict(String name) {
        String key = name == null ? "" : name.strip();
        if (key.isEmpty()) {
            return false;
        }
        try {
            return aiJdbcTemplate.update(
                    "DELETE FROM scenic_spot_ai_info WHERE spot_key = ?", key) > 0;
        } catch (Exception e) {
            log.warn("景点 AI 详情缓存清除失败：{}", key, e);
            return false;
        }
    }

    /** 从行程内容里提取【地点名】，供计划生成后批量预生成 */
    public static List<String> extractSpotNames(String content) {        List<String> names = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return names;
        }
        Matcher matcher = POI_PATTERN.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1).strip();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private void triggerGenerate(String key) {
        inflight.computeIfAbsent(key, k -> CompletableFuture.runAsync(() -> {
            try {
                generateAndPersist(k);
                failures.remove(k);
            } catch (Exception e) {
                log.warn("景点 AI 详情生成失败：{}", k, e);
                failures.merge(k, new long[]{1, System.currentTimeMillis()}, (old, cur) -> {
                    old[0] += 1;
                    old[1] = cur[1];
                    return old;
                });
            } finally {
                inflight.remove(k);
            }
        }));
    }

    private ScenicInfoVO queryCache(String key) {
        try {
            List<Map<String, Object>> rows = aiJdbcTemplate.queryForList(
                    "SELECT name, intro, tips, images FROM scenic_spot_ai_info WHERE spot_key = ?", key);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            return ScenicInfoVO.builder()
                    .status("DONE")
                    .name((String) row.get("name"))
                    .intro((String) row.get("intro"))
                    .tips(parseJsonArray((String) row.get("tips")))
                    .images(parseJsonArray((String) row.get("images")))
                    .build();
        } catch (Exception e) {
            // 缓存读不到按未生成处理；生成后的 upsert 会把表带回来
            log.warn("景点 AI 详情缓存读取失败：{}", key, e);
            return null;
        }
    }

    private void generateAndPersist(String key) {
        List<RagHit> hits = retrieveKnowledge(key);
        String context = buildContext(hits);

        String intro = null;
        List<String> tips = List.of();
        try {
            String systemPrompt = "你是旅游内容编辑。有站内资料时必须依据资料整理，不得编造资料中没有的事实、"
                    + "数字或典故；没有资料时可以写客观的通识介绍，但不得编造票价、开放时间、电话等易变信息。"
                    + "只输出一个 JSON 对象，不要输出任何解释、前后缀或代码围栏。";
            String userPayload = context.isBlank()
                    ? "请为景点「" + key + "」写一段客观的通用介绍（120 字左右：它是什么、看什么、有什么特色），"
                    + "并给 2 到 3 条通行游览提示。不要提及资料缺失。\n"
                    + "格式：{\"intro\":\"...\",\"tips\":[\"...\"]}"
                    : "以下是从站内游记与攻略中检索到的资料：\n" + context
                    + "\n\n请据此为景点「" + key + "」整理内容，格式："
                    + "{\"intro\":\"150 字左右的介绍，讲清它看什么、有什么特色\","
                    + "\"tips\":[\"3 到 5 条游览建议，每条一句话，尽量来自资料\"]}";

            String raw = LlmCalls.text(chatModel, systemPrompt, userPayload);
            JsonNode node = objectMapper.readTree(stripCodeFence(raw));
            intro = node.path("intro").asText("");
            tips = readStringArray(node.path("tips"));
        } catch (Exception e) {
            log.warn("景点文案整理失败：{}", key, e);
        }
        if (intro == null || intro.isBlank()) {
            // 模型不可用时退回到检索原文，至少不是空白页
            intro = context.isBlank() ? "「" + key + "」" + FALLBACK_INTRO : truncate(context, 400);
        }

        List<String> images = collectImages(key, hits);

        aiJdbcTemplate.update(
                "INSERT INTO scenic_spot_ai_info (spot_key, name, intro, tips, images) VALUES (?,?,?,?,?) "
                        + "ON CONFLICT (spot_key) DO UPDATE SET "
                        + "intro = EXCLUDED.intro, tips = EXCLUDED.tips, images = EXCLUDED.images",
                key, key, intro, toJson(tips), toJson(images));
        log.info("景点 AI 详情生成完成：{}，图片 {} 张", key, images.size());
    }

    /**
     * 站内检索：游记与攻略里关于该景点的真实经验。
     * <p>
     * 向量检索只看语义相似度，站内没有该景点时照样会返回"其他景点"的片段——
     * 实测「黄鹤楼」曾召回恭王府、天坛的游记，直接喂给模型会让文案写成"资料未涵盖"、
     * 封面图也张冠李戴。因此这里必须做一次<b>名称相关性过滤</b>：
     * 只有正文或标题里真的出现该景点（核心词）的命中才算数，否则宁可当作"站内无资料"。
     */
    private List<RagHit> retrieveKnowledge(String key) {
        try {
            List<RagHit> hits = ragSearchService.search(
                    key + " 景点 介绍 游玩 攻略", RAG_TOP_K, RAG_THRESHOLD, null);
            if (hits == null) {
                return List.of();
            }
            String core = coreName(key);
            List<RagHit> relevant = new ArrayList<>();
            for (RagHit hit : hits) {
                if (isRelevant(hit, core)) {
                    relevant.add(hit);
                }
            }
            if (relevant.isEmpty()) {
                log.info("站内无「{}」的相关资料，改用通识介绍", key);
            }
            return relevant;
        } catch (Exception e) {
            log.warn("景点资料检索失败：{}", key, e);
            return List.of();
        }
    }

    private boolean isRelevant(RagHit hit, String core) {
        if (core.isEmpty()) {
            return false;
        }
        String text = hit.getText() == null ? "" : hit.getText();
        if (text.contains(core)) {
            return true;
        }
        Map<String, Object> metadata = hit.getMetadata();
        if (metadata != null) {
            Object title = metadata.get("title");
            return title != null && title.toString().contains(core);
        }
        return false;
    }

    /** 「黄鹤楼公园」「东湖风景区」这类写法去掉通用后缀再比对，避免因后缀差异漏判 */
    private String coreName(String key) {
        String core = key.replaceAll("(风景区|景区|公园|博物院|博物馆|古城|旅游区|文化区|度假区)$", "").strip();
        return core.isEmpty() ? key : core;
    }

    private String buildContext(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RagHit hit : hits) {
            if (hit.getText() == null || hit.getText().isBlank()) {
                continue;
            }
            if (sb.length() > CONTEXT_MAX_CHARS) {
                break;
            }
            sb.append("- ").append(hit.getText().strip()).append('\n');
        }
        return truncate(sb.toString(), CONTEXT_MAX_CHARS);
    }

    /**
     * 图片只取站内已有素材：官方景点配图 → RAG 命中文档封面。都没有就返回空，由前端占位。
     */
    private List<String> collectImages(String key, List<RagHit> hits) {
        Set<String> images = new LinkedHashSet<>();
        // 1) 业务库里的景点官方图。站内景点表很小（当前 10 条），全量取回内存做双向名称匹配，
        //    比拼 LIKE（正向/反向各一次、还要处理占位符）更可靠，也能覆盖「东湖听涛景区」↔「东湖」
        try {
            String core = coreName(key);
            for (ScenicSpots spot : scenicSpotsService.list()) {
                if (images.size() >= MAX_IMAGES) {
                    break;
                }
                String spotName = spot.getName();
                if (spotName == null || spotName.isBlank()) {
                    continue;
                }
                if (spotName.contains(core) || core.contains(spotName)) {
                    addImage(images, spot.getImageUrl());
                }
            }
        } catch (Exception e) {
            log.warn("站内景点配图查询失败：{}", key, e);
        }
        // 2) RAG 命中文档的封面（游记/攻略首图）
        if (hits != null) {
            for (RagHit hit : hits) {
                Map<String, Object> metadata = hit.getMetadata();
                if (metadata != null) {
                    Object cover = metadata.get("cover");
                    if (cover != null) {
                        addImage(images, cover.toString());
                    }
                }
                if (images.size() >= MAX_IMAGES) {
                    break;
                }
            }
        }
        return new ArrayList<>(images);
    }

    private void addImage(Set<String> images, String rawUrl) {
        if (images.size() >= MAX_IMAGES || rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        String url = rawUrl.strip();
        // 库里存的是相对路径（imgs/places/xxx.jpg），补上前导斜杠才能被前端静态映射命中
        if (!url.startsWith("http") && !url.startsWith("/")) {
            url = "/" + url;
        }
        images.add(url);
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String text = item.asText("");
                if (!text.isBlank()) {
                    list.add(text.strip());
                }
            });
        }
        return list;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return readStringArray(objectMapper.readTree(json));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** 模型偶尔会包一层 ```json 围栏，剥掉再解析 */
    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        return text.strip()
                .replaceFirst("^```[a-zA-Z]*\\s*", "")
                .replaceFirst("```\\s*$", "")
                .strip();
    }
}
