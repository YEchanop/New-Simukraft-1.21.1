package client.cn.kafei.simukraft.client.citizen.ai;

import common.cn.kafei.simukraft.config.ClientConfig;
import common.cn.kafei.simukraft.network.citizen.chat.CitizenChatContextResponsePacket;
import com.google.gson.Gson;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CitizenAiChatService: 市民 AI 聊天后端（仅客户端）。
 *
 * <p>负责：
 * <ul>
 *   <li>维护与单个市民的 ChatSession（system prompt + 对话历史 + 上下文截断）</li>
 *   <li>向兼容 OpenAI Chat Completions 协议的端点发送 HTTP 请求并解析响应</li>
 *   <li>按错误分类码（code）统一抛出 CitizenAiChatException，不含敏感信息</li>
 *   <li>提供连通性测试（GET /v1/models）供设置面板使用</li>
 * </ul>
 *
 * <p>安全约定：本类不写任何 SLF4J 日志，所有对外抛出的异常消息都不含 Authorization / apiKey。
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenAiChatService {

    private static final ExecutorService HTTP_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Simukraft-Ai-Chat");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(HTTP_EXECUTOR)
            .build();

    private static final Gson GSON = new Gson();

    /**
     * 跨对话框实例的会话历史缓存：key = citizenId + "@" + endpoint.baseUrl()。
     * 同一市民 + 同一网站再次打开对话框时复用上次对话历史（不含 system 消息，system 由最新 ctx 重建）。
     * 切换网站或市民时 key 不同自然新建会话；「清空上下文」按钮显式清除对应缓存。
     */
    private static final java.util.Map<String, java.util.List<ChatMessage>> HISTORY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** historyCacheKey: 按 市民 + 端点 baseUrl 聚合，base 为空时回退 "default" 避免空 key。 */
    public static String historyCacheKey(UUID citizenId, String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.strip();
        if (base.isEmpty()) base = "default";
        return citizenId.toString() + "@" + base;
    }

    /** loadHistory: 返回缓存中非 system 消息的副本；无缓存返回 null。 */
    public static java.util.List<ChatMessage> loadHistory(UUID citizenId, String baseUrl) {
        java.util.List<ChatMessage> cached = HISTORY_CACHE.get(historyCacheKey(citizenId, baseUrl));
        if (cached == null) return null;
        return new java.util.ArrayList<>(cached);
    }

    /** saveHistory: 把 session.history 中非 system 的消息快照存入缓存（关闭对话框后仍保留）。 */
    public static void saveHistory(UUID citizenId, String baseUrl, java.util.List<ChatMessage> history) {
        if (history == null) return;
        java.util.List<ChatMessage> snapshot = new java.util.ArrayList<>();
        for (ChatMessage m : history) {
            if (m == null || "system".equals(m.role)) continue;
            snapshot.add(new ChatMessage(m.role(), m.content()));
        }
        HISTORY_CACHE.put(historyCacheKey(citizenId, baseUrl), snapshot);
    }

    /** clearHistory: 清除指定 市民 + 端点 的缓存历史（「清空上下文」调用）。 */
    public static void clearHistory(UUID citizenId, String baseUrl) {
        HISTORY_CACHE.remove(historyCacheKey(citizenId, baseUrl));
    }

    private CitizenAiChatService() {
    }

    // ========================================================================
    // 3.2 数据结构
    // ========================================================================

    /** ChatMessage: 单条聊天消息。role ∈ {"system","user","assistant"}；其它值调用方自行忽略。 */
    public static class ChatMessage {
        public final String role;
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role == null ? "" : role;
            this.content = content == null ? "" : content;
        }

        public String role() { return role; }
        public String content() { return content; }
    }

    /** CitizenAiContext: 对 CitizenChatContextResponsePacket 的轻量包装，供未来扩展。 */
    public static class CitizenAiContext {
        public final CitizenChatContextResponsePacket packet;

        public CitizenAiContext(CitizenChatContextResponsePacket packet) {
            this.packet = packet;
        }
    }

    /**
     * ChatSession: 与单个市民的对话会话。
     * history[0] 永远是 system 消息（构建时注入，截断时保留）；history[1..] 为 user/assistant 交替对。
     */
    public static class ChatSession {
        public final UUID citizenId;
        public final ClientConfig.AiEndpoint endpoint;
        public final ClientConfig.AiModel model;
        public final List<ChatMessage> history;
        public int maxHistoryPairs = 10;

        public ChatSession(UUID citizenId, ClientConfig.AiEndpoint ep, ClientConfig.AiModel m,
                           CitizenChatContextResponsePacket ctx) {
            this.citizenId = citizenId;
            this.endpoint = ep;
            this.model = m;
            this.history = new ArrayList<>();
            String sysPrompt = buildSystemPrompt(ctx, "");
            this.history.add(new ChatMessage("system", sysPrompt));
        }

        /** append: 追加一条通用消息（内部使用 + 供 sendMessage 按步骤调用）。超过上限时从 index=1 移除最老一对。 */
        public void append(ChatMessage msg) {
            history.add(msg);
            trimHistory();
        }

        /** append: 追加用户消息。 */
        public void append(String userText) {
            append(new ChatMessage("user", userText == null ? "" : userText));
        }

        /** appendAssistant: 追加助理回复。 */
        public void appendAssistant(String assistantText) {
            append(new ChatMessage("assistant", assistantText == null ? "" : assistantText));
        }

        /** clearHistoryPairs: 保留 system，清空所有对话消息对。 */
        public void clearHistoryPairs() {
            if (history.size() > 1) {
                history.subList(1, history.size()).clear();
            }
        }

        /** lastUser: 返回最后一条 role=user 的内容，用于「重新生成」。无则返回 ""。 */
        public String lastUser() {
            for (int i = history.size() - 1; i >= 0; i--) {
                ChatMessage m = history.get(i);
                if ("user".equals(m.role)) {
                    return m.content;
                }
            }
            return "";
        }

        private void trimHistory() {
            int maxMessages = maxHistoryPairs * 2 + 1; // system + N*(user+assistant)
            while (history.size() > maxMessages) {
                // 移除最老一对（index=1 和 index=2），共 2 条
                if (history.size() >= 3) {
                    history.remove(1);
                    history.remove(1);
                } else {
                    break;
                }
            }
        }
    }

    // ========================================================================
    // 3.7 异常分类
    // ========================================================================

    /** CitizenAiChatException: 统一异常类，携带分类码 code 便于 UI 翻译。 */
    public static class CitizenAiChatException extends RuntimeException {
        public final String code;

        public CitizenAiChatException(String code, String message) {
            super(message);
            this.code = code == null ? "unknown" : code;
        }

        public CitizenAiChatException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code == null ? "unknown" : code;
        }

        public String code() { return code; }
    }

    // ========================================================================
    // 3.3 System prompt 拼装
    // ========================================================================

    /**
     * buildSystemPrompt: 根据上下文与玩家语言生成 system prompt。
     * 字段为空时自动跳过对应子句，不输出 null。
     */
    public static String buildSystemPrompt(CitizenChatContextResponsePacket ctx, String playerLang) {
        boolean isZh = false;
        if (playerLang != null) {
            String lang = playerLang.toLowerCase(Locale.ROOT);
            isZh = lang.startsWith("zh") || lang.contains("zh_cn") || lang.contains("zh_tw") || lang.contains("zh_hans") || lang.contains("zh_hant");
        }
        if (ctx == null) {
            return isZh ? defaultPromptZh() : defaultPromptEn();
        }

        StringBuilder sb = new StringBuilder();
        List<String> clauses = new ArrayList<>();

        // 开头身份句
        String name = safe(ctx.name());
        int age = ctx.age();
        String gender = safe(ctx.gender());
        String genderText = "male".equalsIgnoreCase(gender)
                ? (isZh ? "男性" : "male")
                : ("female".equalsIgnoreCase(gender)
                        ? (isZh ? "女性" : "female")
                        : (gender.isEmpty() ? "" : gender));

        StringBuilder identity = new StringBuilder();
        if (isZh) {
            identity.append("你现在扮演一名在 Minecraft 模拟城市里的普通市民 NPC。");
            if (!name.isEmpty()) identity.append("你的名字叫 `").append(name).append("`");
            if (age > 0) {
                if (!name.isEmpty()) identity.append("，");
                identity.append(age).append(" 岁");
            }
            if (!genderText.isEmpty()) {
                if (age > 0 || !name.isEmpty()) identity.append("，");
                identity.append(genderText);
            }
            if (!name.isEmpty() || age > 0 || !genderText.isEmpty()) identity.append("。");
        } else {
            identity.append("You are now role-playing an ordinary citizen NPC in a Minecraft Sim-U-Kraft city.");
            if (!name.isEmpty() || age > 0 || !genderText.isEmpty()) {
                identity.append(" You are");
                if (!name.isEmpty()) identity.append(" `").append(name).append("`");
                if (age > 0) identity.append(", ").append(age).append(" years old");
                if (!genderText.isEmpty()) identity.append(", ").append(genderText);
                identity.append(".");
            }
        }
        clauses.add(identity.toString());

        // 职业与工作状态
        String job = safe(ctx.jobKey());
        String workStatus = safe(ctx.workStatusKey());
        if (!job.isEmpty() || !workStatus.isEmpty()) {
            StringBuilder jobSb = new StringBuilder();
            if (isZh) {
                jobSb.append("职业：");
                jobSb.append(job.isEmpty() ? (isZh ? "无" : "none") : job);
                if (!workStatus.isEmpty()) {
                    jobSb.append("（最近工作状态：").append(workStatus).append("）");
                }
                jobSb.append("。");
            } else {
                jobSb.append("Job: ").append(job.isEmpty() ? "unemployed" : job);
                if (!workStatus.isEmpty()) {
                    jobSb.append(" (recent work status: ").append(workStatus).append(")");
                }
                jobSb.append(".");
            }
            clauses.add(jobSb.toString());
        }

        // 城市
        String cityName = safe(ctx.cityName());
        String cityLevel = safe(ctx.cityLevel());
        if (!cityName.isEmpty() || !cityLevel.isEmpty()) {
            StringBuilder citySb = new StringBuilder();
            if (isZh) {
                citySb.append("所在城市：");
                citySb.append(cityName.isEmpty() ? (isZh ? "未知" : "unknown") : cityName);
                if (!cityLevel.isEmpty()) {
                    citySb.append("（").append(cityLevel).append(" 级）");
                }
                citySb.append("。");
            } else {
                citySb.append("City: ").append(cityName.isEmpty() ? "unknown" : cityName);
                if (!cityLevel.isEmpty()) {
                    citySb.append(" (level ").append(cityLevel).append(")");
                }
                citySb.append(".");
            }
            clauses.add(citySb.toString());
        }

        // 性格
        String personality = safe(ctx.personalityBrief());
        if (!personality.isEmpty()) {
            if (isZh) clauses.add("性格：" + personality + "。");
            else clauses.add("Personality: " + personality + ".");
        }

        // 兴趣爱好
        String hobbies = safe(ctx.hobbies());
        if (!hobbies.isEmpty()) {
            if (isZh) clauses.add("兴趣爱好：" + hobbies + "。");
            else clauses.add("Hobbies: " + hobbies + ".");
        }

        // 家庭关系
        String familyRole = safe(ctx.familyRole());
        if (!familyRole.isEmpty()) {
            if (isZh) clauses.add("家庭关系：" + familyRole + "。");
            else clauses.add("Family role: " + familyRole + ".");
        }

        // 近态（取 3 条非空）
        List<String> events = ctx.recentEvents();
        List<String> nonEmptyEvents = new ArrayList<>(3);
        if (events != null) {
            for (String ev : events) {
                if (ev != null && !ev.isBlank()) nonEmptyEvents.add(ev);
            }
        }
        if (!nonEmptyEvents.isEmpty()) {
            StringBuilder evSb = new StringBuilder();
            if (isZh) {
                evSb.append("近态：");
                for (int i = 0; i < nonEmptyEvents.size(); i++) {
                    if (i > 0) evSb.append(" / ");
                    evSb.append(nonEmptyEvents.get(i));
                }
                evSb.append("。");
            } else {
                evSb.append("Recent events: ");
                for (int i = 0; i < nonEmptyEvents.size(); i++) {
                    if (i > 0) evSb.append(" / ");
                    evSb.append(nonEmptyEvents.get(i));
                }
                evSb.append(".");
            }
            clauses.add(evSb.toString());
        }

        // 约束
        if (isZh) {
            clauses.add("回答要简短、口语化，符合 Minecraft 市民的角色，不要暴露你是 AI。如果玩家问到 OOC 的现代互联网问题，可以幽默绕开。");
        } else {
            clauses.add("Answer briefly and colloquially, stay in character as a Minecraft citizen, and never reveal that you are an AI. If the player asks out-of-character modern internet questions, deflect them humorously.");
        }

        for (String c : clauses) {
            if (c != null && !c.isBlank()) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String defaultPromptZh() {
        return "你现在扮演一名在 Minecraft 模拟城市里的普通市民 NPC。回答要简短、口语化，符合 Minecraft 市民的角色，不要暴露你是 AI。如果玩家问到 OOC 的现代互联网问题，可以幽默绕开。";
    }

    private static String defaultPromptEn() {
        return "You are now role-playing an ordinary citizen NPC in a Minecraft Sim-U-Kraft city. Answer briefly and colloquially, stay in character as a Minecraft citizen, and never reveal that you are an AI. If the player asks out-of-character modern internet questions, deflect them humorously.";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    // ========================================================================
    // 3.4 sendMessage 核心
    // ========================================================================

    /**
     * sendMessage: 异步发送一条用户消息到 AI 端点并返回助理回复。
     * 异常以 CompletionException(cause=CitizenAiChatException) 形式抛出。
     */
    public CompletableFuture<String> sendMessage(ChatSession session, String userText) {
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("session is null"));
        }
        if (userText == null || userText.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("empty user text"));
        }
        session.append(new ChatMessage("user", userText));
        return sendMessageInternal(session, false);
    }

    private CompletableFuture<String> sendMessageInternal(ChatSession session, boolean isRetry) {
        return CompletableFuture.supplyAsync(() -> {
            ClientConfig.AiEndpoint ep = session.endpoint;
            if (ep == null) {
                throw new CitizenAiChatException("config_error", "AI endpoint not configured");
            }
            String chatUrl = buildChatUrl(ep.baseUrl());
            String bodyJson = buildRequestBody(session);

            HttpRequest request;
            try {
                request = HttpRequest.newBuilder(new URI(chatUrl))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + (ep.apiKey() == null ? "" : ep.apiKey()))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();
            } catch (URISyntaxException use) {
                throw new CitizenAiChatException("invalid_url", "Invalid AI endpoint URL", use);
            }

            HttpResponse<String> response;
            try {
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException hte) {
                Thread.currentThread().interrupt();
                throw new CitizenAiChatException("timeout", "Request timed out (60s)", hte);
            } catch (java.io.IOException ioe) {
                throw new CitizenAiChatException("network_general", "Network I/O error", ioe);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new CitizenAiChatException("network_general", "Request interrupted", ie);
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                String content = parseChatCompletionContent(response.body());
                session.appendAssistant(content);
                return content;
            }
            // 错误分类
            throw mapHttpError(status);
        }, HTTP_EXECUTOR).handle((result, throwable) -> {
            if (result != null) {
                return CompletableFuture.completedFuture(result);
            }
            CitizenAiChatException cae = unwrapChatException(throwable);
            // 重试：仅 network_general / timeout / server_error，且仅重试 1 次
            if (!isRetry && cae != null) {
                String c = cae.code();
                if ("network_general".equals(c) || "timeout".equals(c) || "server_error".equals(c)) {
                    // 回滚最后一条 user，重试时会重新 append 吗？不：sendMessage 已经 append 过 userText，
                    // 重试不需要重新 append。但若 assistant 侧失败，history 里会多一条 user（无对应 assistant）。
                    // 方案：保持现状（下次 sendMessage 会使 history 出现 user+user，
                    // 但 ChatSession.trimHistory 不校验角色交替，实际请求体是 "messages" 列表，
                    // 少数 API 对顺序严格；我们通过移除最后一条失败的 user 来修复）。
                    // 回滚：history 最后一条是本次失败的 user，移除它，让重试逻辑通过重新 append 时保持一致？
                    // 实际上 sendMessage 已经在外面 append 过了。重试时不会再次调用 sendMessage，所以 history 里的 user 保留即可，
                    // 重试只是再次调用 HTTP。不需要移除。
                    return sendMessageInternal(session, true);
                }
            }
            if (cae != null) {
                CompletableFuture<String> f = new CompletableFuture<>();
                f.completeExceptionally(cae);
                return f;
            }
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(throwable);
            return f;
        }).thenCompose(f -> f);
    }

    private static CitizenAiChatException unwrapChatException(Throwable t) {
        if (t == null) return null;
        Throwable cur = t;
        while (cur instanceof CompletionException) {
            cur = cur.getCause();
        }
        if (cur instanceof CitizenAiChatException cae) return cae;
        return null;
    }

    private static CitizenAiChatException mapHttpError(int status) {
        String readable;
        String code;
        if (status == 401 || status == 403) {
            code = "auth_failed";
            readable = "Authentication failed (HTTP " + status + ")";
        } else if (status == 404) {
            code = "not_found";
            readable = "Endpoint not found (HTTP 404)";
        } else if (status == 429) {
            code = "rate_limit";
            readable = "Rate limited (HTTP 429)";
        } else if (status >= 500 && status < 600) {
            code = "server_error";
            readable = "Server error (HTTP " + status + ")";
        } else if (status >= 400) {
            code = "client_error_" + status;
            readable = "Client error (HTTP " + status + ")";
        } else {
            code = "http_error_" + status;
            readable = "Unexpected HTTP status " + status;
        }
        return new CitizenAiChatException(code, readable);
    }

    // ========================================================================
    // URL / 请求体 / 响应解析 辅助
    // ========================================================================

    /** buildChatUrl: 将 baseUrl 规范化为 chat completions 端点。 */
    public static String buildChatUrl(String baseUrl) {
        String safe = baseUrl == null ? "" : baseUrl.strip();
        // strip trailing '/'
        while (!safe.isEmpty() && safe.charAt(safe.length() - 1) == '/') {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + "/v1/chat/completions";
    }

    /** buildModelsUrl: 规范化为 models 端点（连通性测试用）。 */
    private static String buildModelsUrl(String baseUrl) {
        String safe = baseUrl == null ? "" : baseUrl.strip();
        while (!safe.isEmpty() && safe.charAt(safe.length() - 1) == '/') {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe + "/v1/models";
    }

    /** buildRequestBody: 用 Gson 组装 chat completions 请求 JSON。 */
    @SuppressWarnings("unchecked")
    private static String buildRequestBody(ChatSession session) {
        List<Map<String, String>> messages = buildMessagesList(session.history);
        Map<String, Object> body = Map.of(
                "model", session.model == null ? "" : session.model.id(),
                "messages", messages,
                "temperature", 0.7,
                "max_tokens", 800
        );
        return GSON.toJson(body);
    }

    /** buildMessagesList: 把 history 转成 API 所需的 Map 列表（便于 Gson 序列化）。 */
    public static List<Map<String, String>> buildMessagesList(List<ChatMessage> history) {
        List<Map<String, String>> list = new ArrayList<>(history == null ? 0 : history.size());
        if (history == null) return list;
        for (ChatMessage m : history) {
            if (m == null) continue;
            list.add(Map.of("role", m.role(), "content", m.content()));
        }
        return list;
    }

    /** parseChatCompletionContent: 从响应 JSON 提取 choices[0].message.content。 */
    @SuppressWarnings("unchecked")
    private static String parseChatCompletionContent(String body) {
        if (body == null || body.isBlank()) {
            throw new CitizenAiChatException("parse_failed", "Empty response body");
        }
        Map<String, Object> root;
        try {
            root = GSON.fromJson(body, Map.class);
        } catch (RuntimeException re) {
            throw new CitizenAiChatException("parse_failed", "Invalid JSON response", re);
        }
        if (root == null) {
            throw new CitizenAiChatException("parse_failed", "Null JSON root");
        }
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            // 尝试取 error.message
            String errMsg = extractError(root);
            throw new CitizenAiChatException("parse_failed", "Missing 'choices' in response" + (errMsg.isEmpty() ? "" : ": " + errMsg));
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            throw new CitizenAiChatException("parse_failed", "Invalid choices[0] type");
        }
        Object msgObj = choice.get("message");
        if (!(msgObj instanceof Map<?, ?> message)) {
            throw new CitizenAiChatException("parse_failed", "Missing choices[0].message");
        }
        Object content = message.get("content");
        if (!(content instanceof String strContent)) {
            throw new CitizenAiChatException("parse_failed", "Missing or invalid choices[0].message.content");
        }
        return strContent;
    }

    @SuppressWarnings("unchecked")
    private static String extractError(Map<String, Object> root) {
        try {
            Object e = root.get("error");
            if (e instanceof Map<?, ?> errMap) {
                Object m = errMap.get("message");
                if (m instanceof String s && !s.isBlank()) {
                    // 脱敏：不泄露可能含 key 的内容，仅取前 60 字符
                    String clipped = s.length() > 60 ? s.substring(0, 60) + "..." : s;
                    return clipped;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    // ========================================================================
    // 3.5 错误码 → 可读错误消息
    // ========================================================================

    /**
     * translateError: 把 CitizenAiChatException 按语言转成可读提示。
     * lang 传 "" 或非 zh 走英文；zh 开头或 zh_cn / zh_tw 走中文。
     */
    public static String translateError(CitizenAiChatException e, String lang) {
        if (e == null) {
            return isZhLang(lang) ? "未知错误" : "Unknown error";
        }
        String code = e.code();
        boolean zh = isZhLang(lang);
        return switch (code) {
            case "auth_failed" -> zh
                    ? "认证失败（401/403），请在 AI 设置检查 Key"
                    : "Authentication failed (401/403). Please check your API key in AI settings.";
            case "not_found" -> zh
                    ? "端点未找到（404），请检查 baseUrl 是否包含 /v1"
                    : "Endpoint not found (404). Verify the base URL (should include API root, not /v1).";
            case "rate_limit" -> zh
                    ? "请求过于频繁（429），请稍后再试"
                    : "Rate limited (429). Please try again later.";
            case "server_error" -> zh
                    ? "AI 服务端错误（5xx），请稍后再试"
                    : "AI server error (5xx). Please try again later.";
            case "network_general" -> zh
                    ? "网络连接失败，请检查网络或端点地址"
                    : "Network error. Check your connection and endpoint URL.";
            case "timeout" -> zh
                    ? "请求超时（60s），请稍后再试"
                    : "Request timed out (60s). Please try again later.";
            case "parse_failed" -> zh
                    ? "响应格式无法解析，可能模型不兼容或端点异常"
                    : "Failed to parse response; incompatible model or endpoint issue.";
            case "invalid_url" -> zh
                    ? "AI 端点地址格式错误"
                    : "Invalid AI endpoint URL format.";
            case "config_error" -> zh
                    ? "AI 端点未配置，请先在设置添加"
                    : "AI endpoint not configured. Add one in settings first.";
            default -> {
                if (code != null && code.startsWith("client_error_")) {
                    yield zh
                            ? "客户端请求错误（HTTP " + code.substring("client_error_".length()) + "）"
                            : "Client request error (HTTP " + code.substring("client_error_".length()) + ")";
                }
                yield zh ? "聊天请求失败：" + (e.getMessage() == null ? code : e.getMessage())
                        : "Chat request failed: " + (e.getMessage() == null ? code : e.getMessage());
            }
        };
    }

    private static boolean isZhLang(String lang) {
        if (lang == null || lang.isBlank()) return false;
        String l = lang.toLowerCase(Locale.ROOT);
        return l.startsWith("zh") || l.contains("zh_cn") || l.contains("zh_tw")
                || l.contains("zh_hans") || l.contains("zh_hant");
    }

    // ========================================================================
    // 3.6 连通性测试（供 Task4 设置面板使用）
    // ========================================================================

    /**
     * testModelsEndpointReachable: 异步测试端点 /v1/models 是否可达并返回 2xx。
     * 任何失败（网络 / 超时 / 非 2xx）都完成 false，永不抛异常。
     */
    public CompletableFuture<Boolean> testModelsEndpointReachable(ClientConfig.AiEndpoint ep) {
        if (ep == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            String url = buildModelsUrl(ep.baseUrl());
            HttpRequest request;
            try {
                request = HttpRequest.newBuilder(new URI(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + (ep.apiKey() == null ? "" : ep.apiKey()))
                        .GET()
                        .build();
            } catch (URISyntaxException use) {
                return false;
            }
            try {
                HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                return status >= 200 && status < 300;
            } catch (Exception ignored) {
                return false;
            }
        }, HTTP_EXECUTOR);
    }

    // ========================================================================
    // 3.8 详细连通测试（含错误码，供设置面板展示可读错误）
    // ========================================================================

    /** ReachabilityDetail: 连通测试结果 — success + 可读错误消息（中文）。 */
    public record ReachabilityDetail(boolean success, String message) {}

    /**
     * testModelsEndpointDetail: 异步测试 /v1/models 连通性，返回带可读错误（中文）的详细结果。
     * 永不抛异常；任何异常都包装为 success=false + 中文描述。
     */
    public CompletableFuture<ReachabilityDetail> testModelsEndpointDetail(ClientConfig.AiEndpoint ep) {
        if (ep == null) {
            return CompletableFuture.completedFuture(new ReachabilityDetail(false, "端点未配置"));
        }
        return CompletableFuture.supplyAsync(() -> {
            String url = buildModelsUrl(ep.baseUrl());
            HttpRequest request;
            try {
                request = HttpRequest.newBuilder(new URI(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + (ep.apiKey() == null ? "" : ep.apiKey()))
                        .GET()
                        .build();
            } catch (URISyntaxException use) {
                return new ReachabilityDetail(false, "地址格式错误");
            }
            try {
                HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return new ReachabilityDetail(true, "OK");
                }
                // 用 mapHttpError → translateError 转中文
                CitizenAiChatException ex = mapHttpError(status);
                return new ReachabilityDetail(false, translateError(ex, "zh_cn"));
            } catch (HttpTimeoutException hte) {
                Thread.currentThread().interrupt();
                return new ReachabilityDetail(false, "请求超时（15s）");
            } catch (java.io.IOException ioe) {
                return new ReachabilityDetail(false, "网络连接失败");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new ReachabilityDetail(false, "请求被中断");
            } catch (Exception e) {
                return new ReachabilityDetail(false, "未知错误");
            }
        }, HTTP_EXECUTOR);
    }

    // ========================================================================
    // 3.9 批量拉取模型列表（GET /v1/models 解析 data[].id）
    // ========================================================================

    /** ModelsResponse: 仅用于 Gson 反序列化 /v1/models 响应。 */
    private static final class ModelsResponse {
        List<ModelEntry> data;
    }
    private static final class ModelEntry {
        String id;
    }

    /**
     * fetchModelIds: GET {baseUrl}/v1/models，解析 data[].id，返回模型 id 列表。
     * 失败时返回空列表，永不抛异常。
     */
    public CompletableFuture<List<String>> fetchModelIds(ClientConfig.AiEndpoint ep) {
        if (ep == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> {
            String url = buildModelsUrl(ep.baseUrl());
            HttpRequest request;
            try {
                request = HttpRequest.newBuilder(new URI(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + (ep.apiKey() == null ? "" : ep.apiKey()))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
            } catch (URISyntaxException use) {
                return List.<String>of();
            }
            try {
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (!(status >= 200 && status < 300)) {
                    return List.<String>of();
                }
                String body = response.body();
                if (body == null || body.isBlank()) {
                    return List.<String>of();
                }
                ModelsResponse resp = GSON.fromJson(body, ModelsResponse.class);
                if (resp == null || resp.data == null) {
                    return List.<String>of();
                }
                List<String> ids = new ArrayList<>();
                for (ModelEntry me : resp.data) {
                    if (me != null && me.id != null && !me.id.isBlank()) {
                        ids.add(me.id.trim());
                    }
                }
                return ids;
            } catch (Exception ignored) {
                return List.<String>of();
            }
        }, HTTP_EXECUTOR);
    }

    // ========================================================================
    // 单例访问点（保持与其它 Service 一致的风格）
    // ========================================================================

    private static final CitizenAiChatService INSTANCE = new CitizenAiChatService();

    public static CitizenAiChatService instance() {
        return INSTANCE;
    }
}
