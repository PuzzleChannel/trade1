package mc.simpletrading.mixin;

import mc.simpletrading.economy.TradeManager;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles our chat actions without registering player-facing commands.
 * The player identity is taken from the authenticated connection, never from click payload data.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin {
    @Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
    private void simpletrading$handleTradeChatClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!TradeManager.ACCEPT_CLICK.equals(packet.id())
                && !TradeManager.REJECT_CLICK.equals(packet.id())) {
            return;
        }

        if ((Object) this instanceof ServerGamePacketListenerImpl playListener) {
            ServerPlayer player = playListener.getPlayer();
            TradeManager.getInstance().handleChatClick(player, packet.id());
        }
        ci.cancel();
    }
}
