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
}
