package common.cn.kafei.simukraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public final class ClientConfig {
    public static final String DEFAULT_HUD_ANCHOR = "TOP_RIGHT";
    public static final int DEFAULT_HUD_POS_X = -5;
    public static final int DEFAULT_HUD_POS_Y = 5;
    public static final int DEFAULT_HUD_MAX_WIDTH = 0;
    public static final String DEFAULT_TOAST_ANCHOR = "TOP_RIGHT";
    public static final int DEFAULT_TOAST_POS_X = 0;
    public static final int DEFAULT_TOAST_POS_Y = 0;
    public static final int DEFAULT_TOAST_WIDTH = 184;
    public static final int DEFAULT_TOAST_HEIGHT = 48;
    public static final int DEFAULT_RTS_MOVE_HOLD_SECONDS = 1;
    public static final String DEFAULT_SKIN_CATALOG_URL = "https://littleskin.cn/skinlib";
    public static final String DEFAULT_SKIN_CATALOG_NAME = "LittleSkin";

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_POS_X;
    public static final ModConfigSpec.IntValue HUD_POS_Y;
    public static final ModConfigSpec.IntValue HUD_MAX_WIDTH;
    public static final ModConfigSpec.ConfigValue<String> TOAST_ANCHOR;
    public static final ModConfigSpec.IntValue TOAST_POS_X;
    public static final ModConfigSpec.IntValue TOAST_POS_Y;
    public static final ModConfigSpec.IntValue TOAST_WIDTH;
    public static final ModConfigSpec.IntValue TOAST_HEIGHT;
    public static final ModConfigSpec.BooleanValue PATH_DEBUG_REQUEST_ON_TOGGLE;
    public static final ModConfigSpec.BooleanValue RTS_TARGET_SIMUKRAFT_BLOCKS;
    public static final ModConfigSpec.BooleanValue RTS_TARGET_VANILLA_BLOCKS;
    public static final ModConfigSpec.BooleanValue RTS_TARGET_OTHER_MOD_BLOCKS;
    public static final ModConfigSpec.IntValue RTS_MOVE_HOLD_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> SKIN_CATALOG_URL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SKIN_CATALOG_LIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CITIZEN_AI_ENDPOINTS;
    public static final ModConfigSpec.ConfigValue<String> CITIZEN_AI_DEFAULT_ENDPOINT_ID;
    public static final ModConfigSpec.ConfigValue<String> CITIZEN_AI_DEFAULT_MODEL_ID;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("hud");
        HUD_ENABLED = builder
                .comment("Whether the Sim-U-Kraft HUD is displayed.")
                .translation("config.simukraft.client.hud.enabled")
                .define("enabled", true);
        HUD_ANCHOR = builder
                .comment("HUD anchor: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, BOTTOM_CENTER.")
                .translation("config.simukraft.client.hud.anchor")
                .define("anchor", DEFAULT_HUD_ANCHOR, ClientConfig::isHudAnchor);
        HUD_POS_X = builder
                .comment("HUD X offset from the selected anchor.")
                .translation("config.simukraft.client.hud.posX")
                .defineInRange("posX", DEFAULT_HUD_POS_X, -4096, 4096);
        HUD_POS_Y = builder
                .comment("HUD Y offset from the selected anchor.")
                .translation("config.simukraft.client.hud.posY")
                .defineInRange("posY", DEFAULT_HUD_POS_Y, -4096, 4096);
        HUD_MAX_WIDTH = builder
                .comment("HUD max line width in pixels. 0 = single line (no wrap).")
                .translation("config.simukraft.client.hud.maxWidth")
                .defineInRange("maxWidth", DEFAULT_HUD_MAX_WIDTH, 0, 2048);
        builder.pop();
        builder.push("toast");
        TOAST_ANCHOR = builder
                .comment("Toast anchor: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER, BOTTOM_CENTER.")
                .translation("config.simukraft.client.toast.anchor")
                .define("anchor", DEFAULT_TOAST_ANCHOR, ClientConfig::isHudAnchor);
        TOAST_POS_X = builder
                .comment("Toast X offset from the selected anchor.")
                .translation("config.simukraft.client.toast.posX")
                .defineInRange("posX", DEFAULT_TOAST_POS_X, -4096, 4096);
        TOAST_POS_Y = builder
                .comment("Toast Y offset from the selected anchor.")
                .translation("config.simukraft.client.toast.posY")
                .defineInRange("posY", DEFAULT_TOAST_POS_Y, -4096, 4096);
        TOAST_WIDTH = builder
                .comment("Toast width in pixels.")
                .translation("config.simukraft.client.toast.width")
                .defineInRange("width", DEFAULT_TOAST_WIDTH, 120, 512);
        TOAST_HEIGHT = builder
                .comment("Toast height in pixels. Text scales and wraps to fit.")
                .translation("config.simukraft.client.toast.height")
                .defineInRange("height", DEFAULT_TOAST_HEIGHT, 36, 160);
        builder.pop();
        builder.push("path_debug");
        PATH_DEBUG_REQUEST_ON_TOGGLE = builder
                .comment("Whether Alt+P requests latest NPC paths from the server when path debug is shown.")
                .translation("config.simukraft.client.pathDebug.requestOnToggle")
                .define("requestOnToggle", true);
        builder.pop();
        builder.push("rts");
        RTS_TARGET_SIMUKRAFT_BLOCKS = builder
                .comment("Allow the RTS cursor to target Sim-U-Kraft blocks.")
                .translation("config.simukraft.client.rts.targetSimukraftBlocks")
                .define("targetSimukraftBlocks", true);
        RTS_TARGET_VANILLA_BLOCKS = builder
                .comment("Allow the RTS cursor to target vanilla Minecraft blocks.")
                .translation("config.simukraft.client.rts.targetVanillaBlocks")
                .define("targetVanillaBlocks", true);
        RTS_TARGET_OTHER_MOD_BLOCKS = builder
                .comment("Allow the RTS cursor to target blocks from other mods.")
                .translation("config.simukraft.client.rts.targetOtherModBlocks")
                .define("targetOtherModBlocks", true);
        RTS_MOVE_HOLD_SECONDS = builder
                .comment("Seconds required to hold the left mouse button before RTS movement starts.")
                .translation("config.simukraft.client.rts.moveHoldSeconds")
                .defineInRange("moveHoldSeconds", DEFAULT_RTS_MOVE_HOLD_SECONDS, 1, 10);
        builder.pop();
        builder.push("citizen_skin");
        SKIN_CATALOG_URL = builder
                .comment("URL of the citizen skin catalog API used by the skin download center. Default points to LittleSkin skinlib (littleskin.cn). " + "Generic endpoints must return a JSON array of {\"name\": \"...\", \"url\": \"...\"} entries.")
                .translation("config.simukraft.client.citizenSkin.catalogUrl")
                .define("catalogUrl", DEFAULT_SKIN_CATALOG_URL);
        SKIN_CATALOG_LIST = builder
                .comment("Saved skin catalog APIs as \"name|url\" entries. Use the download center's API manager to add, switch and remove.")
                .translation("config.simukraft.client.citizenSkin.catalogList")
                .defineList("catalogList", List.of(DEFAULT_SKIN_CATALOG_NAME + "|" + DEFAULT_SKIN_CATALOG_URL), ClientConfig::isCatalogEntry);
        builder.pop();
        builder.push("citizenAi");
        CITIZEN_AI_ENDPOINTS = builder
                .comment(
                        "Saved AI endpoints used by citizen AI chat as \"id|alias|baseUrl|apiKey|protocol|enabled|modelsCsv\" entries.",
                        "modelsCsv uses semicolons to separate models; each model is \"modelId:name:enabled:default\".",
                        "enabled/default use 0 or 1; default=1 marks the global default model of the endpoint.",
                        "Use the AI settings panel to add, edit and remove endpoints."
                )
                .translation("config.simukraft.client.citizenAi.endpoints")
                .defineListAllowEmpty("endpoints", List.of(
                        "builtin-nvidia|NVIDIA NIM|https://integrate.api.nvidia.com||openai|1|",
                        "builtin-sensenova|SenseNova (商汤日日新)|https://token.sensenova.cn||openai|1|",
                        "builtin-modelscope|ModelScope (魔搭)|https://api-inference.modelscope.cn||openai|1|",
                        "builtin-openrouter|OpenRouter|https://openrouter.ai/api||openai|1|"
                ), ClientConfig::isAiEndpointEntry);
        CITIZEN_AI_DEFAULT_ENDPOINT_ID = builder
                .comment("Id of the default AI endpoint selected when opening the chat dialog. Empty string means first enabled endpoint.")
                .translation("config.simukraft.client.citizenAi.defaultEndpointId")
                .define("defaultEndpointId", "");
        CITIZEN_AI_DEFAULT_MODEL_ID = builder
                .comment("Id of the default AI model under the default endpoint. Empty string means first enabled model of the endpoint.")
                .translation("config.simukraft.client.citizenAi.defaultModelId")
                .define("defaultModelId", "");
        CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP = builder
                .comment(
                        "Per-endpoint last selected model as \"endpointId|modelId\" entries.",
                        "Automatically updated whenever a citizen AI chat switches models within an endpoint."
                )
                .translation("config.simukraft.client.citizenAi.endpointLastModelMap")
                .defineListAllowEmpty("endpointLastModelMap", List.of(), ClientConfig::isEndpointLastModelEntry);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    /** hudEnabled: 判断 HUD 是否启用。 */
    public static boolean hudEnabled() {
        return HUD_ENABLED.get();
    }

    /** hudAnchorName: 获取规范化 HUD 锚点名。 */
    public static String hudAnchorName() {
        String value = HUD_ANCHOR.get();
        return isHudAnchor(value) ? value.toUpperCase(Locale.ROOT) : DEFAULT_HUD_ANCHOR;
    }

    /** hudPosX: 获取 HUD X 偏移。 */
    public static int hudPosX() {
        return HUD_POS_X.get();
    }

    /** hudPosY: 获取 HUD Y 偏移。 */
    public static int hudPosY() {
        return HUD_POS_Y.get();
    }

    /** pathDebugRequestOnToggle: 判断显示寻路调试时是否请求服务端刷新。 */
    public static boolean pathDebugRequestOnToggle() {
        return PATH_DEBUG_REQUEST_ON_TOGGLE.get();
    }

    /** isRtsTargetBlockEnabled: 按命名空间判断 RTS 光标是否允许命中方块。 */
    public static boolean isRtsTargetBlockEnabled(BlockState state) {
        if (state == null) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if ("simukraft".equals(key.getNamespace())) {
            return RTS_TARGET_SIMUKRAFT_BLOCKS.get();
        }
        if ("minecraft".equals(key.getNamespace())) {
            return RTS_TARGET_VANILLA_BLOCKS.get();
        }
        return RTS_TARGET_OTHER_MOD_BLOCKS.get();
    }

    /** rtsMoveHoldSeconds: 返回 RTS 长按移动所需秒数。 */
    public static int rtsMoveHoldSeconds() {
        return Math.max(1, Math.min(10, RTS_MOVE_HOLD_SECONDS.get()));
    }

    /** skinCatalogUrl: 返回皮肤下载中心目录 API 地址，未配置时为空串。 */
    public static String skinCatalogUrl() {
        String value = SKIN_CATALOG_URL.get();
        return value == null ? "" : value.trim();
    }

    /** setSkinCatalogUrl: 保存皮肤目录 API 地址并写盘（下载中心自定义 API 使用）。 */
    public static void setSkinCatalogUrl(String url) {
        SKIN_CATALOG_URL.set(url == null ? "" : url.trim());
        SPEC.save();
    }

    /** CatalogApi: 已保存的皮肤目录 API（展示名 + 地址）。 */
    public record CatalogApi(String name, String url) {
    }

    /** catalogApis: 返回已保存的 API 列表（配置项按 "name|url" 存储）。 */
    public static List<CatalogApi> catalogApis() {
        List<CatalogApi> result = new ArrayList<>();
        for (String entry : SKIN_CATALOG_LIST.get()) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf('|');
            if (separator < 0) {
                result.add(new CatalogApi(entry, entry));
            } else {
                result.add(new CatalogApi(entry.substring(0, separator), entry.substring(separator + 1)));
            }
        }
        return result;
    }

    /** addCatalogApi: 添加或更新（同地址改名）一个 API 并写盘。 */
    public static void addCatalogApi(String name, String url) {
        String safeUrl = url == null ? "" : url.trim();
        String safeName = name == null || name.isBlank() ? safeUrl : name.trim();
        List<String> entries = new ArrayList<>(SKIN_CATALOG_LIST.get());
        String newEntry = safeName + "|" + safeUrl;
        for (int i = 0; i < entries.size(); i++) {
            String existing = entries.get(i);
            String existingUrl = existing.indexOf('|') >= 0 ? existing.substring(existing.indexOf('|') + 1) : existing;
            if (existingUrl.equals(safeUrl)) {
                entries.set(i, newEntry);
                SKIN_CATALOG_LIST.set(entries);
                SPEC.save();
                return;
            }
        }
        entries.add(newEntry);
        SKIN_CATALOG_LIST.set(entries);
        SPEC.save();
    }

    /** removeCatalogApi: 删除指定地址的 API；若删掉的是当前使用的，自动切回第一个。 */
    public static void removeCatalogApi(String url) {
        String safeUrl = url == null ? "" : url.trim();
        String activeUrl = skinCatalogUrl();
        List<String> entries = new ArrayList<>(SKIN_CATALOG_LIST.get());
        entries.removeIf(entry -> {
            String existingUrl = entry.indexOf('|') >= 0 ? entry.substring(entry.indexOf('|') + 1) : entry;
            return existingUrl.equals(safeUrl);
        });
        if (entries.isEmpty()) {
            entries.add(DEFAULT_SKIN_CATALOG_NAME + "|" + DEFAULT_SKIN_CATALOG_URL);
        }
        SKIN_CATALOG_LIST.set(entries);
        if (safeUrl.equals(activeUrl)) {
            List<CatalogApi> apis = catalogApis();
            setSkinCatalogUrl(apis.isEmpty() ? "" : apis.getFirst().url());
        }
        SPEC.save();
    }

    /** isCatalogEntry: 配置元素校验，条目必须形如 "name|url"。 */
    private static boolean isCatalogEntry(Object value) {
        return value instanceof String string && string.contains("|");
    }

    /** hudMaxWidth: 获取 HUD 最大行宽（0=不限制）。 */
    public static int hudMaxWidth() {
        return HUD_MAX_WIDTH.get();
    }

    /** toastAnchorName: 获取规范化通知锚点名称。 */
    public static String toastAnchorName() {
        String value = TOAST_ANCHOR.get();
        return isHudAnchor(value) ? value.toUpperCase(Locale.ROOT) : DEFAULT_TOAST_ANCHOR;
    }

    /** toastPosX: 获取通知 X 偏移。 */
    public static int toastPosX() {
        return TOAST_POS_X.get();
    }

    /** toastPosY: 获取通知 Y 偏移。 */
    public static int toastPosY() {
        return TOAST_POS_Y.get();
    }

    /** toastWidth: 获取通知宽度。 */
    public static int toastWidth() {
        return TOAST_WIDTH.get();
    }

    /** toastHeight: 获取通知高度。 */
    public static int toastHeight() {
        return TOAST_HEIGHT.get();
    }

    /** resetHudDefaults: 重置 HUD 位置到默认值。 */
    public static void resetHudDefaults() {
        HUD_ANCHOR.set(DEFAULT_HUD_ANCHOR);
        HUD_POS_X.set(DEFAULT_HUD_POS_X);
        HUD_POS_Y.set(DEFAULT_HUD_POS_Y);
        HUD_MAX_WIDTH.set(DEFAULT_HUD_MAX_WIDTH);
        SPEC.save();
    }

    /** resetToastDefaults: 重置通知布局为默认值。 */
    public static void resetToastDefaults() {
        TOAST_ANCHOR.set(DEFAULT_TOAST_ANCHOR);
        TOAST_POS_X.set(DEFAULT_TOAST_POS_X);
        TOAST_POS_Y.set(DEFAULT_TOAST_POS_Y);
        TOAST_WIDTH.set(DEFAULT_TOAST_WIDTH);
        TOAST_HEIGHT.set(DEFAULT_TOAST_HEIGHT);
        SPEC.save();
    }

    /** isHudAnchor: 校验 HUD 锚点配置值。 */
    private static boolean isHudAnchor(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            return false;
        }
        return switch (string.toUpperCase(Locale.ROOT)) {
            case "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "TOP_CENTER", "BOTTOM_CENTER" -> true;
            default -> false;
        };
    }

    // ============================
    // Citizen AI Chat - data model
    // ============================

    /** AiModel: 单个 AI 模型描述。 */
    public record AiModel(String id, String name, boolean enabled, boolean isDefault) {
    }

    /** AiEndpoint: AI 服务端点（域名+鉴权+协议+模型列表）。 */
    public record AiEndpoint(String id, String alias, String baseUrl, String apiKey, String protocol, boolean enabled, List<AiModel> models) {
    }

    /** maskApiKey: 日志与 UI 侧 API Key 脱敏，绝不返回完整明文。 */
    public static String maskApiKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 4) {
            return "****";
        }
        return key.substring(0, 4) + "***";
    }

    /** isAiEndpointEntry: 配置元素校验，至少包含 6 个 | 分隔符（7 段）。 */
    private static boolean isAiEndpointEntry(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            return false;
        }
        int count = 0;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == '|') {
                count++;
            }
        }
        return count >= 6;
    }

    /** isEndpointLastModelEntry: 配置元素校验，条目必须形如 "endpointId|modelId"，且两段均非空。 */
    private static boolean isEndpointLastModelEntry(Object value) {
        if (!(value instanceof String string) || string.isBlank()) {
            return false;
        }
        int separator = string.indexOf('|');
        if (separator <= 0 || separator == string.length() - 1) {
            return false;
        }
        // 不允许出现第二个 | 分隔符（保持 endpointId|modelId 两段结构）
        return string.indexOf('|', separator + 1) < 0;
    }

    /** listAiEndpoints: 解析 CITIZEN_AI_ENDPOINTS 列表为强类型对象，格式错误条目跳过。 */
    public static List<AiEndpoint> listAiEndpoints() {
        List<AiEndpoint> result = new ArrayList<>();
        List<? extends String> raw = CITIZEN_AI_ENDPOINTS.get();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split("\\|", -1);
            if (parts.length < 7) {
                continue;
            }
            String id = parts[0] == null ? "" : parts[0].trim();
            String alias = parts[1] == null ? "" : parts[1].trim();
            String baseUrl = parts[2] == null ? "" : parts[2].trim();
            String apiKey = parts[3] == null ? "" : parts[3];
            String protocol = parts[4] == null ? "" : parts[4].trim();
            String enabledStr = parts[5] == null ? "1" : parts[5].trim();
            boolean enabled = !"0".equals(enabledStr);
            String modelsCsv = parts[6] == null ? "" : parts[6];

            if (id.isEmpty()) {
                continue;
            }

            List<AiModel> models = new ArrayList<>();
            if (!modelsCsv.isEmpty()) {
                String[] modelParts = modelsCsv.split(";", -1);
                for (String modelEntry : modelParts) {
                    if (modelEntry == null || modelEntry.isBlank()) {
                        continue;
                    }
                    String[] m = modelEntry.split(":", -1);
                    if (m.length < 4) {
                        continue;
                    }
                    String mid = m[0] == null ? "" : m[0].trim();
                    String mname = m[1] == null ? "" : m[1].trim();
                    String menabled = m[2] == null ? "1" : m[2].trim();
                    String mdefault = m[3] == null ? "0" : m[3].trim();
                    if (mid.isEmpty()) {
                        continue;
                    }
                    models.add(new AiModel(
                            mid,
                            mname,
                            !"0".equals(menabled),
                            "1".equals(mdefault)
                    ));
                }
            }

            result.add(new AiEndpoint(id, alias, baseUrl, apiKey, protocol, enabled, models));
        }
        return result;
    }

    /** serializeAiEndpoint: 把 AiEndpoint 序列化为配置字符串。 */
    private static String serializeAiEndpoint(AiEndpoint ep, String targetDefaultModelId) {
        StringBuilder sb = new StringBuilder();
        sb.append(ep.id() == null ? "" : ep.id().trim()).append('|');
        sb.append(ep.alias() == null ? "" : ep.alias().trim()).append('|');
        sb.append(ep.baseUrl() == null ? "" : ep.baseUrl().trim()).append('|');
        sb.append(ep.apiKey() == null ? "" : ep.apiKey()).append('|');
        sb.append(ep.protocol() == null ? "" : ep.protocol().trim()).append('|');
        sb.append(ep.enabled() ? '1' : '0').append('|');

        List<AiModel> models = ep.models();
        if (models != null && !models.isEmpty()) {
            boolean first = true;
            for (AiModel m : models) {
                if (!first) sb.append(';');
                first = false;
                boolean isDefaultFlag = (targetDefaultModelId != null && targetDefaultModelId.equals(m.id()));
                sb.append(m.id() == null ? "" : m.id().trim()).append(':');
                sb.append(m.name() == null ? "" : m.name().trim()).append(':');
                sb.append(m.enabled() ? '1' : '0').append(':');
                sb.append(isDefaultFlag ? '1' : '0');
            }
        }
        return sb.toString();
    }

    /** addAiEndpoint: 新增或覆盖（同 id）端点并写盘；若有默认模型同步设置默认。 */
    public static void addAiEndpoint(AiEndpoint ep) {
        if (ep == null || ep.id() == null || ep.id().isBlank()) {
            return;
        }
        String defaultModelId = null;
        List<AiModel> models = ep.models();
        if (models != null) {
            for (AiModel m : models) {
                if (m.isDefault()) {
                    defaultModelId = m.id();
                    break;
                }
            }
        }

        List<String> entries = new ArrayList<>();
        List<? extends String> raw = CITIZEN_AI_ENDPOINTS.get();
        if (raw != null) {
            for (String e : raw) {
                if (e == null) continue;
                String[] parts = e.split("\\|", -1);
                if (parts.length >= 1 && ep.id().equals(parts[0].trim())) {
                    continue; // 去旧
                }
                entries.add(e);
            }
        }
        entries.add(serializeAiEndpoint(ep, defaultModelId));
        CITIZEN_AI_ENDPOINTS.set(entries);

        if (defaultModelId != null) {
            setAiDefault(ep.id(), defaultModelId); // 内部会 save
        } else {
            SPEC.save();
        }
    }

    /** updateAiEndpoint: 同 addAiEndpoint，同 id 覆盖并写盘。 */
    public static void updateAiEndpoint(AiEndpoint ep) {
        addAiEndpoint(ep);
    }

    /** removeAiEndpoint: 按 id 移除端点；若移除的是当前默认端点，自动回退第一个启用项。 */
    public static void removeAiEndpoint(String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return;
        }
        List<String> entries = new ArrayList<>();
        List<? extends String> raw = CITIZEN_AI_ENDPOINTS.get();
        boolean removed = false;
        if (raw != null) {
            for (String e : raw) {
                if (e == null) continue;
                String[] parts = e.split("\\|", -1);
                if (parts.length >= 1 && endpointId.equals(parts[0].trim())) {
                    removed = true;
                    continue;
                }
                entries.add(e);
            }
        }
        CITIZEN_AI_ENDPOINTS.set(entries);

        if (removed) {
            // 同步清理 endpointLastModelMap 中被删端点的记录
            List<String> lastModelEntries = new ArrayList<>();
            List<? extends String> lastModelRaw = CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP.get();
            if (lastModelRaw != null) {
                for (String e : lastModelRaw) {
                    if (e == null || e.isBlank()) {
                        continue;
                    }
                    int separator = e.indexOf('|');
                    if (separator <= 0) {
                        continue;
                    }
                    String eid = e.substring(0, separator).trim();
                    if (!endpointId.equals(eid)) {
                        lastModelEntries.add(e);
                    }
                }
            }
            CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP.set(lastModelEntries);

            String curEpId = CITIZEN_AI_DEFAULT_ENDPOINT_ID.get();
            String curModelId = CITIZEN_AI_DEFAULT_MODEL_ID.get();
            boolean needReset = endpointId.equals(curEpId == null ? "" : curEpId.trim());
            if (!needReset) {
                // 如果被删端点是当前默认 model 的所属端点，也回退
                List<AiEndpoint> remaining = listAiEndpoints();
                boolean found = false;
                for (AiEndpoint ep : remaining) {
                    if ((curModelId != null) && !curModelId.isEmpty()) {
                        for (AiModel mm : ep.models()) {
                            if (curModelId.equals(mm.id()) && endpointId.equals(ep.id())) {
                                needReset = true;
                                break;
                            }
                        }
                    }
                    if (endpointId.equals(ep.id())) {
                        found = true;
                    }
                }
                if (!found && curEpId != null && curEpId.isEmpty()) {
                    needReset = false;
                }
            }
            if (needReset) {
                List<AiEndpoint> remaining = listAiEndpoints();
                String newEpId = "";
                String newModelId = "";
                for (AiEndpoint ep : remaining) {
                    if (ep.enabled()) {
                        newEpId = ep.id();
                        for (AiModel m : ep.models()) {
                            if (m.enabled()) {
                                newModelId = m.id();
                                break;
                            }
                        }
                        break;
                    }
                }
                CITIZEN_AI_DEFAULT_ENDPOINT_ID.set(newEpId);
                CITIZEN_AI_DEFAULT_MODEL_ID.set(newModelId);
            }
        }
        SPEC.save();
    }

    /** setAiDefault: 校验 endpointId/modelId 组合存在后，设置默认值并同步持久化 default 标记。 */
    public static void setAiDefault(String endpointId, String modelId) {
        if (endpointId == null || modelId == null || endpointId.isBlank() || modelId.isBlank()) {
            return;
        }
        List<AiEndpoint> endpoints = listAiEndpoints();
        AiEndpoint targetEp = null;
        AiModel targetModel = null;
        for (AiEndpoint ep : endpoints) {
            if (endpointId.equals(ep.id())) {
                for (AiModel m : ep.models()) {
                    if (modelId.equals(m.id())) {
                        targetEp = ep;
                        targetModel = m;
                        break;
                    }
                }
                if (targetEp != null) break;
            }
        }
        if (targetEp == null || targetModel == null) {
            return; // 组合不存在，不动作
        }

        // 序列化所有端点，仅目标端点目标模型的 default=1
        List<String> newEntries = new ArrayList<>();
        for (AiEndpoint ep : endpoints) {
            String defModelId = ep.id().equals(targetEp.id()) ? targetModel.id() : null;
            // 找原默认（非目标端点时，保持原 default 标记的模型）
            if (defModelId == null) {
                for (AiModel mm : ep.models()) {
                    if (mm.isDefault()) {
                        defModelId = mm.id();
                        break;
                    }
                }
            }
            newEntries.add(serializeAiEndpoint(ep, defModelId));
        }
        CITIZEN_AI_ENDPOINTS.set(newEntries);
        CITIZEN_AI_DEFAULT_ENDPOINT_ID.set(endpointId);
        CITIZEN_AI_DEFAULT_MODEL_ID.set(modelId);
        SPEC.save();
    }

    /** getLastModelByEndpoint: 根据端点 id 获取其上一次使用的模型 id；未记录时返回空串。 */
    public static String getLastModelByEndpoint(String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return "";
        }
        String safeId = endpointId.trim();
        List<? extends String> raw = CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP.get();
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf('|');
            if (separator <= 0) {
                continue;
            }
            String eid = entry.substring(0, separator).trim();
            if (safeId.equals(eid)) {
                String mid = entry.substring(separator + 1);
                return mid == null ? "" : mid.trim();
            }
        }
        return "";
    }

    /** setLastModelByEndpoint: 设置指定端点最后一次选择的模型 id 并写盘。 */
    public static void setLastModelByEndpoint(String endpointId, String modelId) {
        if (endpointId == null || endpointId.isBlank() || modelId == null || modelId.isBlank()) {
            return;
        }
        String safeEndpointId = endpointId.trim();
        String safeModelId = modelId.trim();
        List<String> entries = new ArrayList<>();
        List<? extends String> raw = CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP.get();
        boolean found = false;
        if (raw != null) {
            for (String e : raw) {
                if (e == null || e.isBlank()) {
                    continue;
                }
                int separator = e.indexOf('|');
                if (separator <= 0) {
                    continue;
                }
                String eid = e.substring(0, separator).trim();
                if (safeEndpointId.equals(eid)) {
                    entries.add(safeEndpointId + "|" + safeModelId);
                    found = true;
                } else {
                    entries.add(e);
                }
            }
        }
        if (!found) {
            entries.add(safeEndpointId + "|" + safeModelId);
        }
        CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP.set(entries);
        SPEC.save();
    }
}
