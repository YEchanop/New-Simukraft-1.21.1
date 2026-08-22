package common.cn.kafei.simukraft.network.city.core;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.city.group.CityUserGroup;
import common.cn.kafei.simukraft.city.group.CityUserGroupService;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public record CityCoreOpCityListRequestPacket(Action action, UUID cityId) implements CustomPacketPayload {
    public static final Type<CityCoreOpCityListRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_op_city_list_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreOpCityListRequestPacket> STREAM_CODEC = StreamCodec.of(CityCoreOpCityListRequestPacket::encode, CityCoreOpCityListRequestPacket::decode);
    private static final long CONFIRM_WINDOW_MS = 10_000L;
    private static final Map<UUID, PendingConfirmation> CONFIRMATIONS = new ConcurrentHashMap<>();

    public enum Action {
        LIST,
        DELETE
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreOpCityListRequestPacket packet) {
        buffer.writeUtf(packet.action().name(), 16);
        buffer.writeUUID(packet.cityId() == null ? new UUID(0L, 0L) : packet.cityId());
    }

    public static CityCoreOpCityListRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        Action action;
        try {
            action = Action.valueOf(buffer.readUtf(16));
        } catch (IllegalArgumentException exception) {
            action = Action.LIST;
        }
        UUID cityId = buffer.readUUID();
        return new CityCoreOpCityListRequestPacket(action, cityId.equals(new UUID(0L, 0L)) ? null : cityId);
    }

    public static void handle(CityCoreOpCityListRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleOnServer(level, player, packet);
        }
    }

    private static void handleOnServer(ServerLevel level, ServerPlayer player, CityCoreOpCityListRequestPacket packet) {
        if (!player.hasPermissions(2)) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.op_required"));
            return;
        }
        if (packet.action() == Action.DELETE && packet.cityId() != null) {
            deleteOrConfirm(level, player, packet.cityId());
            return;
        }
        sendList(level, player, "", false, false);
    }

    private static void deleteOrConfirm(ServerLevel level, ServerPlayer player, UUID cityId) {
        Optional<CityData> city = CityService.findCity(level, cityId);
        if (city.isEmpty()) {
            CONFIRMATIONS.remove(player.getUUID());
            sendList(level, player, "message.simukraft.city_core.op_city_not_found", false, false);
            return;
        }
        long now = System.currentTimeMillis();
        PendingConfirmation pending = CONFIRMATIONS.get(player.getUUID());
        if (pending == null || !pending.cityId().equals(cityId) || now - pending.createdAt() > CONFIRM_WINDOW_MS) {
            CONFIRMATIONS.put(player.getUUID(), new PendingConfirmation(cityId, now));
            sendList(level, player, "message.simukraft.city_core.op_delete_confirm", true, false);
            return;
        }
        CONFIRMATIONS.remove(player.getUUID());
        List<ServerPlayer> onlineMembers = CityUserGroupService.onlinePlayers(level, CityUserGroup.members(cityId));
        boolean deleted = CityService.deleteCityAsOperator(level, cityId, CityChunkManager.get(level), CityPoiManager.get(level));
        if (!deleted) {
            CONFIRMATIONS.remove(player.getUUID());
            sendList(level, player, "message.simukraft.city_core.op_delete_failed", false, false);
            return;
        }
        Component message = Component.translatable("message.simukraft.command.city_delete.success", city.get().cityName());
        CityGroupMessageService.sendResolved(onlineMembers, Component.translatable("toast.simukraft.title"), message, "info", null);
        HudSyncService.syncResolvedGroup(onlineMembers, true);
        CityChunkSyncService.syncToAll(level);
        sendList(level, player, "message.simukraft.city_core.op_city_deleted", false, true);
    }

    private static void sendList(ServerLevel level, ServerPlayer player, String message, boolean confirmationRequired, boolean deleted) {
        List<CityCoreOpCityListResponsePacket.Entry> entries = CityService.allCities(level).stream()
                .sorted(Comparator.comparing(CityData::cityName, String.CASE_INSENSITIVE_ORDER).thenComparing(CityData::cityId))
                .map(city -> new CityCoreOpCityListResponsePacket.Entry(city.cityId(), city.cityName(), mayorName(city), city.members().size(), city.dimensionId(), city.cityCorePos()))
                .toList();
        PacketDistributor.sendToPlayer(player, new CityCoreOpCityListResponsePacket(entries, message, confirmationRequired, deleted));
    }

    private static String mayorName(CityData city) {
        return city.members().stream()
                .filter(member -> member.permissionLevel().power() >= 2)
                .map(member -> member.playerName())
                .findFirst()
                .orElse("-");
    }

    private record PendingConfirmation(UUID cityId, long createdAt) {
    }
}
