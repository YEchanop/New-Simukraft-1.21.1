package common.cn.kafei.simukraft.network.citizen.info;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.citizen.CitizenInfoMenuHolder;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.rts.RtsRemoteCitizenAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 市民信息界面更换皮肤请求（客户端 -> 服务端），skinPath 空串表示恢复默认。 */
@SuppressWarnings("null")
public record CitizenSetSkinPacket(UUID citizenId, String skinPath) implements CustomPacketPayload {
    public static final Type<CitizenSetSkinPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "citizen_set_skin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenSetSkinPacket> STREAM_CODEC =
            StreamCodec.of(CitizenSetSkinPacket::encode, CitizenSetSkinPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** encode：写入目标 UUID 与皮肤路径（限制长度防止越界）。 */
    public static void encode(RegistryFriendlyByteBuf buffer, CitizenSetSkinPacket packet) {
        buffer.writeUUID(packet.citizenId());
        buffer.writeUtf(packet.skinPath() != null ? packet.skinPath() : "", 256);
    }

    /** decode：读取目标 UUID 与皮肤路径。 */
    public static CitizenSetSkinPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CitizenSetSkinPacket(buffer.readUUID(), buffer.readUtf(256));
    }

    /** handle：允许通过市民信息容器（近距离）或市民管理界面（城市 OFFICIAL 及以上权限）修改皮肤。 */
    public static void handle(CitizenSetSkinPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // 分支一：玩家在市民实体信息容器里（原版近距离逻辑）。
        if (player.containerMenu instanceof ModularUIContainerMenu menu
                && menu.uiHolder instanceof CitizenInfoMenuHolder holder
                && holder.citizenId().equals(packet.citizenId())) {
            CitizenEntity citizen = holder.owner();
            if (citizen == null || !citizen.isAlive() || citizen.level() != level
                    || (player.distanceToSqr(citizen) > 64.0D
                    && !RtsRemoteCitizenAccess.hasInfoAccess(player, packet.citizenId()))) {
                return;
            }
            CitizenService.setSkin(level, citizen, sanitize(packet.skinPath()));
            return;
        }
        // 分支二：从市民管理界面修改皮肤，需玩家在该市民所在城市至少拥有 OFFICIAL 权限。
        // 同时 OP 等级 2+ 也可以直接操作（与 OP 城市管理逻辑一致）。
        CitizenManager citizenManager = CitizenManager.get(level);
        if (citizenManager.getCitizen(packet.citizenId()).isEmpty()) {
            return;
        }
        boolean permitted = false;
        if (player.hasPermissions(2)) {
            permitted = true;
        } else {
            UUID cityId = citizenManager.getCitizen(packet.citizenId()).get().cityId();
            if (cityId != null) {
                permitted = CityManager.get(level).getCity(cityId)
                        .map(city -> city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL))
                        .orElse(false);
            }
        }
        if (!permitted) {
            return;
        }
        CitizenEntity target = (CitizenEntity) level.getEntity(packet.citizenId());
        if (target == null || !target.isAlive()) {
            // 未加载的实体仍可通过 CitizenData 直接持久化皮肤路径，下次加载同步即可。
            citizenManager.getCitizen(packet.citizenId()).ifPresent(data -> {
                data.setSkinPath(sanitize(packet.skinPath()));
                CitizenService.save(level, packet.citizenId());
            });
            return;
        }
        CitizenService.setSkin(level, target, sanitize(packet.skinPath()));
    }

    private static String sanitize(String skinPath) {
        if (skinPath == null) {
            return "";
        }
        String trimmed = skinPath.trim();
        return trimmed.length() > 256 ? trimmed.substring(0, 256) : trimmed;
    }
}
