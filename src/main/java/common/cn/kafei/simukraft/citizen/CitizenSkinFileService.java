package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.SimuKraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CitizenSkinFileService: 服务端皮肤文件服务。扫描游戏目录 simukraftskins 文件夹，
 * 把皮肤图片字节缓存起来，供玩家登录或主动刷新时下发给客户端注册渲染。
 * 服务端持有文件后，任意客户端都能加载到同一套自定义皮肤。
 */
@SuppressWarnings("null")
public final class CitizenSkinFileService {
    public static final String ROOT_DIR = "simukraftskins";
    /** 单个皮肤文件大小上限，避免超出 NeoForge 数据包负载上限。 */
    private static final int MAX_SKIN_BYTES = 30_000;

    private static final Map<String, byte[]> SKINS = new LinkedHashMap<>();

    private CitizenSkinFileService() {
    }

    public static Path rootDirectory() {
        return FMLPaths.GAMEDIR.get().resolve(ROOT_DIR);
    }

    /** scan: 重新扫描文件夹并缓存每个皮肤的文件字节。 */
    public static void scan() {
        SKINS.clear();
        Path dir = rootDirectory();
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to create server citizen skin folder {}", dir, exception);
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
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    if (bytes.length > MAX_SKIN_BYTES) {
                        SimuKraft.LOGGER.warn("Simukraft: Citizen skin {} exceeds {} bytes, skipped", fileName, MAX_SKIN_BYTES);
                        continue;
                    }
                    SKINS.put(name, bytes);
                } catch (IOException exception) {
                    SimuKraft.LOGGER.warn("Simukraft: Failed to read citizen skin file {}", fileName, exception);
                }
            }
        } catch (IOException exception) {
            SimuKraft.LOGGER.warn("Simukraft: Failed to scan server citizen skin folder {}", dir, exception);
        }
    }

    public static Collection<String> names() {
        return SKINS.keySet();
    }

    public static byte[] bytesFor(String name) {
        return SKINS.get(name);
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
}
