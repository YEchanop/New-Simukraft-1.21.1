package common.cn.kafei.simukraft.network.citizen.info;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.network.clientbound.ClientboundNetworkBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端向客户端下发单个皮肤文件（文件名 + 图片字节），客户端注册为动态贴图。 */
@SuppressWarnings("null")
public record CitizenSkinTransferPacket(String name, byte[] data) implements CustomPacketPayload {
    public static final Type<CitizenSkinTransferPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "citizen_skin_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenSkinTransferPacket> STREAM_CODEC =
            StreamCodec.of(CitizenSkinTransferPacket::encode, CitizenSkinTransferPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode：写入文件名与图片字节。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CitizenSkinTransferPacket packet) {
        buffer.writeUtf(packet.name() != null ? packet.name() : "", 128);
        buffer.writeByteArray(packet.data() != null ? packet.data() : new byte[0]);
    }

    /** decode：读取文件名与图片字节。 */
    public static CitizenSkinTransferPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenSkinTransferPacket(buffer.readUtf(128), buffer.readByteArray());
    }

    /** handle：在主线程把皮肤注册为动态贴图。 */
    public static void handle(CitizenSkinTransferPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientboundNetworkBridge.handleCitizenSkinTransfer(packet));
    }
}
