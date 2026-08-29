package client.cn.kafei.simukraft.client.citizen;

import common.cn.kafei.simukraft.SimuKraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CitizenSkinLibrary: 扫描游戏目录 simukraftskins 文件夹中的 PNG/JPG，
 * 把每张图片注册为 simukraft:skins/&lt;文件名&gt; 动态贴图，供市民渲染器与头像绘制使用。
 * 皮肤路径（skinPath）以 "simukraft:skins/xxx" 形式存库，文件名去扩展名（_f 结尾表示纤细模型）。
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenSkinLibrary {
    /** 与建筑包 simukraftbuilding 同级约定：游戏目录下独立文件夹。 */
    public static final String ROOT_DIR = "simukraftskins";
    public static final String PREFIX = SimuKraft.MOD_ID + ":skins/";

    private static final Map<String, ResourceLocation> SKINS = new LinkedHashMap<>();
    /** 服务端下发的皮肤名，本地文件夹重扫时保留这些注册。 */
    private static final Set<String> SERVER_SKINS = new HashSet<>();
    /** 下载中心目录缩略图（simukraft:catalog/<name>），仅用于下载中心预览。 */
    private static final Map<String, ResourceLocation> CATALOG_SKINS = new LinkedHashMap<>();
    private static boolean scanned;

    private CitizenSkinLibrary() {
    }

    public static Path rootDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(ROOT_DIR);
    }

    /** ensureScanned: 客户端启动时调用一次；UI 刷新按钮会再次 reload。 */
    public static void ensureScanned() {
        if (!scanned) {
            reload();
        }
    }

    /** registerCatalogThumbnail: 注册下载中心目录缩略图，不进入市民皮肤列表。 */
    public static boolean registerCatalogThumbnail(String name, byte[] data) {
        if (name == null || name.isBlank() || data == null || data.length == 0) {
            return false;
        }
        ResourceLocation rl = catalogTextureLocation(name);
        if (rl == null) {
            return false;
        }
        try (InputStream input = new ByteArrayInputStream(data)) {
            NativeImage image = NativeImage.read(input);
            if (image == null) {
                return false;
            }
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            textureManager.release(rl);
            textureManager.register(rl, new DynamicTexture(image));
            CATALOG_SKINS.put(name.toLowerCase(Locale.ROOT), rl);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to register catalog thumbnail {}", name, exception);
            return false;
        }
    }

    /** clearCatalogThumbnails: 释放全部目录缩略图，打开下载中心前调用。 */
    public static void clearCatalogThumbnails() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation rl : CATALOG_SKINS.values()) {
            textureManager.release(rl);
        }
        CATALOG_SKINS.clear();
    }

    /** catalogTextureLocation: 目录缩略图资源位置，非法名返回 null。 */
    public static ResourceLocation catalogTextureLocation(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "catalog/" + name.toLowerCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** registerFromServer: 注册服务端下发的皮肤文件为动态贴图，同名覆盖本地。 */
    public static void registerFromServer(String name, byte[] data) {
        if (name == null || name.isBlank() || data == null || data.length == 0) {
            return;
        }
        name = name.toLowerCase(Locale.ROOT);
        ResourceLocation rl = skinTextureLocation(name);
        if (rl == null) {
            SimuKraft.LOGGER.warn("Simukraft: Skip server citizen skin with invalid name {}", name);
            return;
        }
        try (InputStream input = new ByteArrayInputStream(data)) {
            NativeImage image = NativeImage.read(input);
            if (image != null) {
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                textureManager.release(rl);
                textureManager.register(rl, new DynamicTexture(image));
                SKINS.put(name, rl);
                SERVER_SKINS.add(name);
            }
        } catch (IOException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to register server citizen skin {}", name, exception);
        }
    }

    /** reload: 重新扫描本地文件夹并重建动态贴图注册，服务端下发的皮肤保留。 */
    public static void reload() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (var iterator = SKINS.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, ResourceLocation> entry = iterator.next();
            if (!SERVER_SKINS.contains(entry.getKey())) {
                textureManager.release(entry.getValue());
                iterator.remove();
            }
        }

        Path dir = rootDirectory();
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to create citizen skin folder {}", dir, exception);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                String name = stripExtension(fileName);
                if (name == null || name.isBlank()) {
                    continue;
                }
                // 文件名统一小写（资源路径只允许小写），服务端同名皮肤优先。
                name = name.toLowerCase(Locale.ROOT);
                if (SERVER_SKINS.contains(name)) {
                    continue;
                }
                ResourceLocation rl = skinTextureLocation(name);
                if (rl == null) {
                    SimuKraft.LOGGER.warn("Simukraft: Skip citizen skin with invalid name {}", fileName);
                    continue;
                }
                try (InputStream input = Files.newInputStream(path)) {
                    NativeImage image = NativeImage.read(input);
                    if (image != null) {
                        textureManager.release(rl);
                        textureManager.register(rl, new DynamicTexture(image));
                        SKINS.put(name, rl);
                    }
                } catch (IOException | IllegalArgumentException exception) {
                    SimuKraft.LOGGER.warn("Simukraft: Skip invalid citizen skin file {}", fileName, exception);
                }
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to scan citizen skin folder {}", dir, exception);
        }
        scanned = true;
    }

    public static List<String> listNames() {
        return new ArrayList<>(SKINS.keySet());
    }

    /** isFolderSkinPath: 是否为文件夹皮肤（simukraft:skins/ 前缀）。 */
    public static boolean isFolderSkinPath(String skinPath) {
        return skinPath != null && skinPath.startsWith(PREFIX);
    }

    /** storedPath: 由文件名生成存库用的皮肤路径。 */
    public static String storedPath(String name) {
        return PREFIX + name;
    }

    /** nameFromStoredPath: 从存库路径解析文件名，非文件夹皮肤返回 null。 */
    public static String nameFromStoredPath(String skinPath) {
        if (!isFolderSkinPath(skinPath)) {
            return null;
        }
        return skinPath.substring(PREFIX.length());
    }

    /** textureLocation: 文件夹皮肤的渲染资源位置，未注册时也按路径解析返回。 */
    public static ResourceLocation textureLocation(String skinPath) {
        String name = nameFromStoredPath(skinPath);
        return skinTextureLocation(name);
    }

    /** hasTexture: 该文件夹皮肤是否已成功注册（文件存在且可解码）。 */
    public static boolean hasTexture(String skinPath) {
        String name = nameFromStoredPath(skinPath);
        return name != null && SKINS.containsKey(name);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!"png".equals(extension) && !"jpg".equals(extension) && !"jpeg".equals(extension)) {
            return null;
        }
        return fileName.substring(0, dot);
    }

    /** skinTextureLocation: 构建皮肤资源位置，非法文件名返回 null 而非抛异常。 */
    private static ResourceLocation skinTextureLocation(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "skins/" + name);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
