package common.cn.kafei.simukraft.network.citizen.chat;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CitizenChatContextRequestPacket: 客户端向服务端请求指定市民的 AI 聊天上下文快照（C2S）。
 *
 * <p>权限校验（双路径，任一满足即可）：
 * <ul>
 *   <li>玩家 OP 等级 >= 2（{@code player.hasPermissions(2)}）</li>
 *   <li>玩家在市民所属城市中持有 {@link CityPermissionLevel#OFFICIAL} 及以上权限</li>
 * </ul>
 *
 * <p>当校验失败、市民不存在、或全局开关关闭时，返回携带对应 errorCode 的 {@link CitizenChatContextResponsePacket}。
 */
@SuppressWarnings("null")
public record CitizenChatContextRequestPacket(UUID cityId, UUID citizenId, BlockPos corePos) implements CustomPacketPayload {
    public static final Type<CitizenChatContextRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "citizen_chat_context_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenChatContextRequestPacket> STREAM_CODEC =
            StreamCodec.of(CitizenChatContextRequestPacket::encode, CitizenChatContextRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // -------------------------------------------------------------------------
    // 编解码
    // -------------------------------------------------------------------------

    public static void encode(RegistryFriendlyByteBuf buffer, CitizenChatContextRequestPacket packet) {
        buffer.writeUUID(packet.cityId() != null ? packet.cityId() : new UUID(0L, 0L));
        buffer.writeUUID(packet.citizenId() != null ? packet.citizenId() : new UUID(0L, 0L));
        buffer.writeBlockPos(packet.corePos() != null ? packet.corePos() : BlockPos.ZERO);
    }

    public static CitizenChatContextRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenChatContextRequestPacket(buffer.readUUID(), buffer.readUUID(), buffer.readBlockPos());
    }

    // -------------------------------------------------------------------------
    // 服务端 handler（运行在 server 网络线程，具体逻辑 enqueue 到主线程）
    // -------------------------------------------------------------------------

    public static void handle(CitizenChatContextRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        context.enqueueWork(() -> handleOnServerThread(packet, player, level));
    }

    private static void handleOnServerThread(CitizenChatContextRequestPacket packet, ServerPlayer player, ServerLevel level) {
        UUID citizenId = packet.citizenId();

        // 1) 全局开关校验
        if (!ServerConfig.enableCitizenAiChat()) {
            sendError(player, citizenId, 3);
            return;
        }

        // 2) 市民存在性
        CitizenManager citizenManager = CitizenManager.get(level);
        CitizenData citizen = citizenManager.getCitizen(citizenId).orElse(null);
        if (citizen == null) {
            sendError(player, citizenId, 2);
            return;
        }

        // 4) 组装正常响应
        CitizenChatContextResponsePacket response = buildResponse(citizen, level);
        PacketDistributor.sendToPlayer(player, response);
    }

    // -------------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------------

    private static void sendError(ServerPlayer player, UUID citizenId, int errorCode) {
        CitizenChatContextResponsePacket error = new CitizenChatContextResponsePacket(
                citizenId,               // citizenId
                "",                      // name
                "",                      // gender
                0,                       // age
                "",                      // jobKey
                "",                      // workStatusKey
                "",                      // cityName
                "",                      // cityLevel
                "",                      // personalityBrief
                "",                      // hobbies
                "",                      // familyRole
                List.of("", "", ""),     // recentEvents (size=3)
                errorCode
        );
        PacketDistributor.sendToPlayer(player, error);
    }

    private static CitizenChatContextResponsePacket buildResponse(CitizenData citizen, ServerLevel level) {
        // 基本字段
        UUID citizenId = citizen.uuid();
        String name = nonNull(citizen.name());
        // gender: CitizenData.gender() 已规范化为 "male" / "female"
        String gender = nonNull(citizen.gender());
        int age = citizen.age();

        // jobKey / workStatusKey：取对应 key，保持本地化对齐
        String jobKey = nonNull(citizen.jobId());
        String workStatusKey = nonNull(citizen.workStatus());

        // 城市信息
        String cityName = "";
        String cityLevel = "";
        UUID cityId = citizen.cityId();
        if (cityId != null) {
            CityData cityData = CityManager.get(level).getCity(cityId).orElse(null);
            if (cityData != null) {
                cityName = nonNull(cityData.cityName());
                cityLevel = String.valueOf(cityData.cityLevel());
            }
        }

        // personalityBrief / hobbies：简单拼接，无详细数据时使用通用默认短语
        String personalityBrief = buildPersonalityBrief(citizen);
        String hobbies = buildHobbies(citizen);

        // familyRole：从家庭关系推断，取不到则返回空串
        String familyRole = buildFamilyRole(citizen, level);

        // recentEvents：固定 3 条，暂以占位文本（后续 Task6 细化）
        List<String> recentEvents = buildRecentEvents(citizen);

        return new CitizenChatContextResponsePacket(
                citizenId,
                name,
                gender,
                age,
                jobKey,
                workStatusKey,
                cityName,
                cityLevel,
                personalityBrief,
                hobbies,
                familyRole,
                recentEvents,
                0 // errorCode = 0 (ok)
        );
    }

    private static String buildPersonalityBrief(CitizenData citizen) {
        StringBuilder sb = new StringBuilder();
        // 根据 happiness/sick/working 等状态简单拼接性格标签
        if (citizen.happiness() >= 80.0D) {
            sb.append("乐观、");
        } else if (citizen.happiness() <= 30.0D) {
            sb.append("有些忧郁、");
        }
        if (citizen.working()) {
            sb.append("认真工作、");
        }
        if (citizen.sick()) {
            sb.append("最近身体不适、");
        }
        if (sb.isEmpty()) {
            sb.append("随和、认真工作、");
        }
        // 去掉末尾顿号
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '、') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String buildHobbies(CitizenData citizen) {
        // 无专门字段时，提供通用默认短语（不 null）
        String base = "散步、阅读";
        // 若市民在工作，附带喜欢的活动
        if (citizen.working()) {
            return base + "、和同事聊天";
        }
        if (citizen.child()) {
            return "玩耍、听故事";
        }
        return base;
    }

    private static String buildFamilyRole(CitizenData citizen, ServerLevel level) {
        UUID familyId = citizen.familyId();
        if (familyId == null) {
            return "";
        }
        // 简单推断：使用 pregnancy / child 等状态提供占位角色
        if (citizen.pregnant()) {
            return "准妈妈";
        }
        if (citizen.child()) {
            return "孩子";
        }
        // 没有详细家庭结构时返回空串（由后续 Task6 细化）
        return "";
    }

    private static List<String> buildRecentEvents(CitizenData citizen) {
        List<String> events = new ArrayList<>(3);
        // 占位最近 3 条事件，保证非 null 且长度 = 3
        if (citizen.working()) {
            events.add("今天正在上班");
        } else {
            events.add("今天正在休息");
        }
        if (citizen.sick()) {
            events.add("刚刚去医院看过病");
        } else {
            events.add("刚刚吃了一餐");
        }
        events.add("最近心情不错");
        // 防御：补齐或截断到 3 条，全部非 null
        while (events.size() < 3) {
            events.add("");
        }
        if (events.size() > 3) {
            events = new ArrayList<>(events.subList(0, 3));
        }
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) == null) events.set(i, "");
        }
        return List.copyOf(events);
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
