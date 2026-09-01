package common.cn.kafei.simukraft.network.citizen.chat;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * CitizenChatContextResponsePacket: 服务端返回给客户端的市民 AI 聊天上下文快照（S2C）。
 *
 * <p><b>errorCode 语义约定</b>：
 * <ul>
 *   <li>0 = 成功（正常数据）</li>
 *   <li>1 = 玩家无权限（OP 不足或城市权限不够）</li>
 *   <li>2 = 市民不存在</li>
 *   <li>3 = 全局 {@code enableCitizenAiChat} 开关关闭</li>
 * </ul>
 *
 * <p><b>客户端等待并取得 Response 的 API（供 Task6 使用）</b>：
 * <pre>{@code
 *   // 1) 创建一个 CompletableFuture 并发送请求
 *   CompletableFuture<CitizenChatContextResponsePacket> future =
 *       CitizenChatContextResponsePacket.requestFuture(citizenId, 10_000L);
 *   PacketDistributor.sendToServer(new CitizenChatContextRequestPacket(cityId, citizenId, corePos));
 *   // 2) 阻塞等待（或使用 future.thenAccept/thenCompose 异步）
 *   CitizenChatContextResponsePacket resp = future.get();
 * }</pre>
 *
 * <p>或直接读取最后一次接收到的响应（不保证匹配特定 citizenId）：
 * <pre>{@code
 *   CitizenChatContextResponsePacket last = CitizenChatContextResponsePacket.lastReceived();
 * }</pre>
 */
@SuppressWarnings("null")
public record CitizenChatContextResponsePacket(
        UUID citizenId,
        String name,
        String gender,
        int age,
        String jobKey,
        String workStatusKey,
        String cityName,
        String cityLevel,
        String personalityBrief,
        String hobbies,
        String familyRole,
        List<String> recentEvents,
        int errorCode
) implements CustomPacketPayload {

    public static final Type<CitizenChatContextResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "citizen_chat_context_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenChatContextResponsePacket> STREAM_CODEC =
            StreamCodec.of(CitizenChatContextResponsePacket::encode, CitizenChatContextResponsePacket::decode);

    // -------------------------------------------------------------------------
    // 内存 holder 与 Future 匹配机制
    // -------------------------------------------------------------------------

    /** lastReceived：最后一次收到的响应快照（volatile，客户端 UI 可快速读取）。 */
    private static volatile CitizenChatContextResponsePacket lastReceived;

    /**
     * pendingFutures：按 citizenId 登记的未完成 Future，客户端 handler 收到包时匹配并完成。
     * ConcurrentHashMap 保证线程安全（网络线程写入，UI 线程读取/登记）。
     */
    private static final ConcurrentMap<UUID, CompletableFuture<CitizenChatContextResponsePacket>> PENDING_FUTURES =
            new ConcurrentHashMap<>();

    /**
     * 返回最后一次收到的响应快照，或 {@code null}（客户端未收到任何响应时）。
     */
    public static CitizenChatContextResponsePacket lastReceived() {
        return lastReceived;
    }

    /**
     * 注册一个针对指定 citizenId 的 CompletableFuture。
     *
     * <p>用法：
     * <ol>
     *   <li>调用本方法取得一个 Future；</li>
     *   <li>紧接着 {@code PacketDistributor.sendToServer(new CitizenChatContextRequestPacket(...))}；</li>
     *   <li>在 UI 线程通过 {@code future.get(timeoutMs, TimeUnit.MILLISECONDS)} 阻塞式等待。</li>
     * </ol>
     *
     * <p>10 秒（或指定 {@code timeoutMs}）未收到对应响应，Future 将以 {@link TimeoutException} 异常完成。
     *
     * @param citizenId  目标市民 UUID
     * @param timeoutMs  超时时长（毫秒），传 {@code <=0} 时默认 10_000ms
     * @return 可等待的 CompletableFuture（永远不会为 null）
     */
    public static CompletableFuture<CitizenChatContextResponsePacket> requestFuture(UUID citizenId, long timeoutMs) {
        final long actualTimeout = timeoutMs > 0 ? timeoutMs : 10_000L;
        CompletableFuture<CitizenChatContextResponsePacket> future = new CompletableFuture<>();
        if (citizenId == null) {
            future.completeExceptionally(new IllegalArgumentException("citizenId is null"));
            return future;
        }
        PENDING_FUTURES.put(citizenId, future);
        // 超时自动淘汰，避免内存泄漏
        future.orTimeout(actualTimeout, TimeUnit.MILLISECONDS).whenComplete((r, t) -> {
            // 无论正常/异常完成，都把自己从 map 里移除
            PENDING_FUTURES.remove(citizenId, future);
        });
        return future;
    }

    /**
     * 供测试/调试使用：清除所有挂起的 Future（不影响 lastReceived）。
     */
    public static void clearPendingFutures() {
        PENDING_FUTURES.clear();
    }

    // -------------------------------------------------------------------------
    // record compact 构造：保证字段规范化，recentEvents 固定长度 3 且无 null 元素
    // -------------------------------------------------------------------------

    public CitizenChatContextResponsePacket {
        name = name != null ? name : "";
        gender = gender != null ? gender : "";
        jobKey = jobKey != null ? jobKey : "";
        workStatusKey = workStatusKey != null ? workStatusKey : "";
        cityName = cityName != null ? cityName : "";
        cityLevel = cityLevel != null ? cityLevel : "";
        personalityBrief = personalityBrief != null ? personalityBrief : "";
        hobbies = hobbies != null ? hobbies : "";
        familyRole = familyRole != null ? familyRole : "";
        // recentEvents：长度归一化到 3，元素非 null
        List<String> normalized = new ArrayList<>(3);
        if (recentEvents != null) {
            for (int i = 0; i < 3 && i < recentEvents.size(); i++) {
                String item = recentEvents.get(i);
                normalized.add(item != null ? item : "");
            }
        }
        while (normalized.size() < 3) {
            normalized.add("");
        }
        recentEvents = Collections.unmodifiableList(normalized);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // -------------------------------------------------------------------------
    // 编解码
    // -------------------------------------------------------------------------

    public static void encode(RegistryFriendlyByteBuf buffer, CitizenChatContextResponsePacket packet) {
        buffer.writeUUID(packet.citizenId() != null ? packet.citizenId() : new UUID(0L, 0L));
        buffer.writeUtf(packet.name(), 64);
        buffer.writeUtf(packet.gender(), 16);
        buffer.writeVarInt(packet.age());
        buffer.writeUtf(packet.jobKey(), 256);
        buffer.writeUtf(packet.workStatusKey(), 256);
        buffer.writeUtf(packet.cityName(), 64);
        buffer.writeUtf(packet.cityLevel(), 16);
        buffer.writeUtf(packet.personalityBrief(), 512);
        buffer.writeUtf(packet.hobbies(), 256);
        buffer.writeUtf(packet.familyRole(), 128);
        // recentEvents：永远按 3 条写入
        List<String> events = packet.recentEvents();
        buffer.writeVarInt(events.size());
        for (String ev : events) {
            buffer.writeUtf(ev != null ? ev : "", 256);
        }
        buffer.writeVarInt(packet.errorCode());
    }

    public static CitizenChatContextResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        UUID citizenId = buffer.readUUID();
        String name = buffer.readUtf(64);
        String gender = buffer.readUtf(16);
        int age = buffer.readVarInt();
        String jobKey = buffer.readUtf(256);
        String workStatusKey = buffer.readUtf(256);
        String cityName = buffer.readUtf(64);
        String cityLevel = buffer.readUtf(16);
        String personalityBrief = buffer.readUtf(512);
        String hobbies = buffer.readUtf(256);
        String familyRole = buffer.readUtf(128);
        int eventCount = buffer.readVarInt();
        List<String> recentEvents = new ArrayList<>(Math.max(3, eventCount));
        for (int i = 0; i < eventCount; i++) {
            recentEvents.add(buffer.readUtf(256));
        }
        int errorCode = buffer.readVarInt();
        return new CitizenChatContextResponsePacket(
                citizenId, name, gender, age, jobKey, workStatusKey, cityName, cityLevel,
                personalityBrief, hobbies, familyRole, recentEvents, errorCode
        );
    }

    // -------------------------------------------------------------------------
    // 客户端 handler（运行在 client 网络线程，通过 enqueueWork 进入主线程）
    // -------------------------------------------------------------------------

    public static void handle(CitizenChatContextResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCitizenChatContextResponse(packet));
    }

    // -------------------------------------------------------------------------
    // 客户端桥接方法（由 ClientboundNetworkBridge 的实际安装实现调用）
    // -------------------------------------------------------------------------

    /**
     * receiveOnClientThread：在客户端主线程保存结果并完成对应 Future。
     *
     * <p>该方法由 {@code ClientboundNetworkHandler} 安装实现委托调用，
     * common 层在无客户端实现时也可直接调用（本类内部逻辑不依赖客户端特有 API）。
     */
    public static void receiveOnClientThread(CitizenChatContextResponsePacket packet) {
        if (packet == null) return;
        lastReceived = packet;
        // 匹配并完成挂起的 Future（按 citizenId）
        if (packet.citizenId() != null) {
            CompletableFuture<CitizenChatContextResponsePacket> future = PENDING_FUTURES.remove(packet.citizenId());
            if (future != null && !future.isDone()) {
                future.complete(packet);
            }
        }
    }
}
