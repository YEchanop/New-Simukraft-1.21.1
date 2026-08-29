package common.cn.kafei.simukraft.network.citizen.info;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenSkinFileService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端请求服务端重扫皮肤文件夹并把全部皮肤文件回发（界面刷新按钮使用）。 */
@SuppressWarnings("null")
public record CitizenSkinRequestPacket() implements CustomPacketPayload {
    public static final Type<CitizenSkinRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "citizen_skin_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenSkinRequestPacket> STREAM_CODEC =
            StreamCodec.of(CitizenSkinRequestPacket::encode, CitizenSkinRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CitizenSkinRequestPacket packet) {
    }

    public static CitizenSkinRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenSkinRequestPacket();
    }

    /** handle：服务端重扫并把全部皮肤文件回发给请求玩家。 */
    public static void handle(CitizenSkinRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CitizenSkinFileService.scan();
            for (String name : CitizenSkinFileService.names()) {
                byte[] bytes = CitizenSkinFileService.bytesFor(name);
                if (bytes != null) {
                    PacketDistributor.sendToPlayer(player, new CitizenSkinTransferPacket(name, bytes));
                }
            }
        }
    }
}
