package client.cn.kafei.simukraft.client.citizen;

import common.cn.kafei.simukraft.SimuKraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CitizenSkinDownloadService: 皮肤资源下载中心的后端。
 * fetchCatalog 从配置的 API 地址拉取皮肤清单（JSON 数组 [{name, url}]），
 * fetchThumbnailAsync 并行抓取每项的缩略图用于列表预览，
 * downloadAsync 按清单下载具体皮肤到 simukraftskins 文件夹。
 * 错误以 errorKey 返回，具体文案由界面按翻译键展示。
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenSkinDownloadService {
    /** 与服务端下发一致的大小上限，保证下载的皮肤也能在多人环境同步。 */
    public static final int MAX_SKIN_BYTES = 30_000;
    private static final int MAX_CATALOG_BYTES = 200_000;
    private static final int MAX_THUMB_BYTES = 300_000;
    private static final int MAX_CATALOG_ENTRIES = 500;
    private static final int MAX_NAME_LENGTH = 64;

    private static final ExecutorService DOWNLOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Simukraft-Skin-Downloader");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService THUMBNAILER = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "Simukraft-Skin-Thumbnailer");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private CitizenSkinDownloadService() {
    }

    /** DownloadResult: 单张皮肤下载结果。success 时 fileName 为保存的文件名，失败时 errorKey 供翻译。 */
    public record DownloadResult(boolean success, String fileName, String errorKey) {
        public static DownloadResult ok(String fileName) {
            return new DownloadResult(true, fileName, "");
        }

        public static DownloadResult fail(String errorKey) {
            return new DownloadResult(false, "", errorKey);
        }
    }

    /** CatalogEntry: 目录中的一张皮肤（name 为存库文件名，url 为图片地址，displayName 为展示名）。 */
    public record CatalogEntry(String name, String url, String displayName) {
        public CatalogEntry(String name, String url) {
            this(name, url, name);
        }

        public CatalogEntry {
            if (displayName == null || displayName.isBlank()) {
                displayName = name;
            }
        }
    }

    /** CatalogResult: 目录拉取结果。success 时 entries 为当前页皮肤，失败时 errorKey 供翻译。 */
    public record CatalogResult(boolean success, String errorKey, List<CatalogEntry> entries, int currentPage, boolean hasNext) {
        public static CatalogResult ok(List<CatalogEntry> entries, int currentPage, boolean hasNext) {
            return new CatalogResult(true, "", entries, currentPage, hasNext);
        }

        public static CatalogResult fail(String errorKey) {
            return new CatalogResult(false, errorKey, List.of(), 1, false);
        }
    }

    /** CatalogQuery: 下载中心查询条件（页号、关键词、排序、类型；sort/type 空串表示默认/全部）。 */
    public record CatalogQuery(int page, String keyword, String sort, String type) {
        public CatalogQuery {
            page = Math.max(1, page);
            keyword = keyword == null ? "" : keyword.trim();
            sort = sort == null ? "" : sort.trim();
            type = type == null ? "" : type.trim();
        }
    }

    /** isValidCatalogUrl: 校验目录地址是否为 http/https 链接。 */
    public static boolean isValidCatalogUrl(String url) {
        return isHttpUrl(url);
    }

    /** downloadAsync: 后台下载皮肤到 simukraftskins，完成后回到客户端主线程回调。 */
    public static void downloadAsync(String url, String preferredName, Consumer<DownloadResult> callback) {
        DOWNLOADER.execute(() -> {
            DownloadResult result = downloadInto(url, preferredName);
            runOnClient(() -> callback.accept(result));
        });
    }

    /** fetchCatalog: 后台拉取皮肤目录清单，完成后回到客户端主线程回调。 */
    public static void fetchCatalog(String catalogUrl, CatalogQuery query, Consumer<CatalogResult> callback) {
        DOWNLOADER.execute(() -> {
            CatalogResult result = readCatalog(catalogUrl, query);
            runOnClient(() -> callback.accept(result));
        });
    }

    /** fetchThumbnailAsync: 后台抓取缩略图并注册为目录预览贴图，完成后回到客户端主线程回调。 */
    public static void fetchThumbnailAsync(String url, String name, Consumer<Boolean> okCallback) {
        THUMBNAILER.execute(() -> {
            byte[] bytes = fetchBytes(url, MAX_THUMB_BYTES);
            runOnClient(() -> okCallback.accept(bytes != null && CitizenSkinLibrary.registerCatalogThumbnail(name, bytes)));
        });
    }

    private static DownloadResult downloadInto(String url, String preferredName) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            return DownloadResult.fail("invalid_url");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return DownloadResult.fail("unsupported_scheme");
        }
        byte[] bytes;
        try {
            HttpResponse<byte[]> response = HTTP.send(
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(20))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return DownloadResult.fail("http_error");
            }
            bytes = response.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            SimuKraft.LOGGER.warn("Simukraft: Failed to download citizen skin from {}", url, exception);
            return DownloadResult.fail("http_error");
        }
        if (bytes == null || bytes.length == 0) {
            return DownloadResult.fail("http_error");
        }
        if (bytes.length > MAX_SKIN_BYTES) {
            return DownloadResult.fail("too_large");
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            if (NativeImage.read(input) == null) {
                return DownloadResult.fail("invalid_image");
            }
        } catch (IOException | IllegalArgumentException exception) {
            return DownloadResult.fail("invalid_image");
        }
        String name = sanitizeName(preferredName);
        if (name.isBlank()) {
            name = sanitizeName(uri);
        }
        Path dir = CitizenSkinLibrary.rootDirectory();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name + ".png"), bytes);
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to save downloaded citizen skin {}", name, exception);
            return DownloadResult.fail("io_failed");
        }
        return DownloadResult.ok(name);
    }

    /** readCatalog: 拉取并解析目录清单，LittleSkin 走专用适配，其余按 {name,url} 清单解析。 */
    private static CatalogResult readCatalog(String catalogUrl, CatalogQuery query) {
        if (isLittleSkin(catalogUrl)) {
            return readLittleSkinCatalog(catalogUrl, query);
        }
        byte[] bytes = fetchBytes(catalogUrl, MAX_CATALOG_BYTES);
        if (bytes == null) {
            return CatalogResult.fail("http_error");
        }
        JsonElement root = parseJson(bytes);
        if (root == null || !root.isJsonArray()) {
            return CatalogResult.fail("invalid_catalog");
        }
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (entries.size() >= MAX_CATALOG_ENTRIES) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String name = object.has("name") && !object.get("name").isJsonNull() ? object.get("name").getAsString() : "";
            String url = object.has("url") && !object.get("url").isJsonNull() ? object.get("url").getAsString() : "";
            String safeName = sanitizeName(name);
            if (safeName.isBlank() || !isHttpUrl(url)) {
                continue;
            }
            entries.add(new CatalogEntry(safeName, url));
        }
        if (entries.isEmpty()) {
            return CatalogResult.fail("catalog_empty");
        }
        return CatalogResult.ok(List.copyOf(entries), 1, false);
    }

    /** LittleSkinItem: LittleSkin 皮肤库列表项，尚未解析贴图哈希。 */
    private record LittleSkinItem(int tid, String displayName, boolean slim) {
    }

    private static final int LITTLE_SKIN_MAX_ENTRIES = 80;
    private static final Map<String, String> LITTLE_SKIN_HEADERS = Map.of(
            "Accept", "application/json",
            "X-Requested-With", "XMLHttpRequest");

    private static boolean isLittleSkin(String catalogUrl) {
        return catalogUrl != null && catalogUrl.toLowerCase(Locale.ROOT).contains("littleskin.cn");
    }

    /** littleSkinHost: 从配置地址提取 LittleSkin 站点根（兼容 http/https）。 */
    private static String littleSkinHost(String catalogUrl) {
        try {
            URI uri = new URI(catalogUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme != null && host != null) {
                return scheme + "://" + host;
            }
        } catch (URISyntaxException ignored) {
        }
        return "https://littleskin.cn";
    }

    /** buildLittleSkinListUrl: 按查询条件构造 LittleSkin 皮肤库列表地址。 */
    private static String buildLittleSkinListUrl(String host, CatalogQuery query) {
        StringBuilder builder = new StringBuilder(host).append("/skinlib/list?page=").append(query.page());
        if (!query.keyword().isBlank()) {
            builder.append("&keyword=").append(URLEncoder.encode(query.keyword(), StandardCharsets.UTF_8));
        }
        if (!query.sort().isBlank()) {
            builder.append("&sort=").append(query.sort());
        }
        if (!query.type().isBlank()) {
            builder.append("&filter=").append(query.type());
        }
        return builder.toString();
    }

    /** readLittleSkinCatalog: 拉取指定页的皮肤库，逐项解析贴图哈希并生成下载条目。 */
    private static CatalogResult readLittleSkinCatalog(String catalogUrl, CatalogQuery query) {
        String host = littleSkinHost(catalogUrl);
        byte[] bytes = fetchBytes(buildLittleSkinListUrl(host, query), MAX_CATALOG_BYTES, LITTLE_SKIN_HEADERS);
        if (bytes == null) {
            return CatalogResult.fail("http_error");
        }
        JsonElement root = parseJson(bytes);
        if (root == null || !root.isJsonObject()) {
            return CatalogResult.fail("invalid_catalog");
        }
        JsonObject rootObject = root.getAsJsonObject();
        JsonArray data = rootObject.getAsJsonArray("data");
        if (data == null) {
            return CatalogResult.fail("catalog_empty");
        }
        List<LittleSkinItem> items = new ArrayList<>();
        for (JsonElement element : data) {
            if (items.size() >= LITTLE_SKIN_MAX_ENTRIES) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!object.has("tid") || object.get("tid").isJsonNull()) {
                continue;
            }
            String type = object.has("type") && !object.get("type").isJsonNull() ? object.get("type").getAsString() : "";
            // 只取可用作玩家皮肤的 steve/alex，跳过披风等其它材质。
            if (!"steve".equals(type) && !"alex".equals(type)) {
                continue;
            }
            int tid = object.get("tid").getAsInt();
            String rawName = object.has("name") && !object.get("name").isJsonNull() ? object.get("name").getAsString() : "";
            items.add(new LittleSkinItem(tid, rawName.isBlank() ? ("skin_" + tid) : rawName, "alex".equals(type)));
        }
        if (items.isEmpty()) {
            return CatalogResult.fail("catalog_empty");
        }
        // 列表项不带贴图哈希，逐项请求详情接口补齐；alex 皮肤存名加 _f 走纤细模型。
        Map<Integer, CatalogEntry> byTid = new LinkedHashMap<>();
        CountDownLatch latch = new CountDownLatch(items.size());
        for (LittleSkinItem item : items) {
            THUMBNAILER.execute(() -> {
                try {
                    byte[] infoBytes = fetchBytes(host + "/skinlib/info/" + item.tid(), MAX_CATALOG_BYTES, LITTLE_SKIN_HEADERS);
                    String hash = infoBytes != null ? readTextureHash(infoBytes) : null;
                    if (hash != null) {
                        String name = "ls_" + item.tid() + (item.slim() ? "_f" : "");
                        byTid.put(item.tid(), new CatalogEntry(name, host + "/textures/" + hash, item.displayName()));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (byTid.isEmpty()) {
            return CatalogResult.fail("http_error");
        }
        JsonElement nextUrl = rootObject.get("next_page_url");
        boolean hasNext = nextUrl != null && !nextUrl.isJsonNull() && !nextUrl.getAsString().isBlank();
        return CatalogResult.ok(new ArrayList<>(byTid.values()), query.page(), hasNext);
    }

    /** readTextureHash: 从 LittleSkin 详情 JSON 提取贴图哈希。 */
    private static String readTextureHash(byte[] bytes) {
        JsonElement root = parseJson(bytes);
        if (root != null && root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            if (object.has("hash") && !object.get("hash").isJsonNull()) {
                return object.get("hash").getAsString();
            }
        }
        return null;
    }

    /** parseJson: 宽松解析 JSON，失败返回 null。 */
    private static JsonElement parseJson(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** fetchBytes: GET 远程资源，任何失败均返回 null（大小超限、非 2xx、网络异常）。 */
    private static byte[] fetchBytes(String url, int maxBytes) {
        return fetchBytes(url, maxBytes, null);
    }

    /** fetchBytes: GET 远程资源，可附加请求头（如 LittleSkin 的 X-Requested-With）。 */
    private static byte[] fetchBytes(String url, int maxBytes, Map<String, String> extraHeaders) {
        if (!isHttpUrl(url)) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            return null;
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET();
            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            HttpResponse<byte[]> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > maxBytes) {
                return null;
            }
            return body;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            SimuKraft.LOGGER.warn("Simukraft: Failed to fetch {}", url, exception);
            return null;
        }
    }

    private static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String scheme = new URI(url).getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static void runOnClient(Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(action);
        }
    }

    /** sanitizeName: 规范为小写 [a-z0-9._-]，非法字符替换为下划线；不可用返回空串。 */
    private static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        String name = builder.toString();
        int end = name.length();
        while (end > 0 && name.charAt(end - 1) == '.') {
            end--;
        }
        name = name.substring(0, end);
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return name;
    }

    /** sanitizeName: 从链接路径提取文件名，用作无显式名称时的回退。 */
    private static String sanitizeName(URI uri) {
        String path = uri.getPath();
        String segment = path == null || path.isBlank() ? "skin" : path;
        int slash = segment.lastIndexOf('/');
        if (slash >= 0 && slash < segment.length() - 1) {
            segment = segment.substring(slash + 1);
        }
        int dot = segment.lastIndexOf('.');
        if (dot > 0) {
            segment = segment.substring(0, dot);
        }
        String name = sanitizeName(segment);
        return name.isBlank() ? "skin" : name;
    }
}