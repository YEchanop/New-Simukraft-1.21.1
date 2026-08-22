package common.cn.kafei.simukraft.network.city.core;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("null")
public record CityCoreOpCityListResponsePacket(List<Entry> cities, String message, boolean confirmationRequired, boolean deleted) implements CustomPacketPayload {
    public static final Type<CityCoreOpCityListResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_op_city_list_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreOpCityListResponsePacket> STREAM_CODEC = StreamCodec.of(CityCoreOpCityListResponsePacket::encode, CityCoreOpCityListResponsePacket::decode);
    private static final int MAX_ENTRIES = 512;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreOpCityListResponsePacket packet) {
        List<Entry> entries = packet.cities() == null ? List.of() : packet.cities().subList(0, Math.min(packet.cities().size(), MAX_ENTRIES));
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.cityId());
            buffer.writeUtf(entry.cityName(), 64);
            buffer.writeUtf(entry.mayorName(), 64);
            buffer.writeVarInt(entry.memberCount());
            buffer.writeUtf(entry.dimensionId(), 128);
            buffer.writeBlockPos(entry.corePos());
        }
        buffer.writeUtf(packet.message() == null ? "" : packet.message(), 128);
        buffer.writeBoolean(packet.confirmationRequired());
        buffer.writeBoolean(packet.deleted());
    }

    public static CityCoreOpCityListResponsePacket decode(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readUtf(64), buffer.readUtf(64), buffer.readVarInt(), buffer.readUtf(128), buffer.readBlockPos()));
        }
        return new CityCoreOpCityListResponsePacket(List.copyOf(entries), buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(CityCoreOpCityListResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCityCoreOpCityListResponse(packet));
    }

    public record Entry(UUID cityId, String cityName, String mayorName, int memberCount, String dimensionId, BlockPos corePos) {
    }
}
