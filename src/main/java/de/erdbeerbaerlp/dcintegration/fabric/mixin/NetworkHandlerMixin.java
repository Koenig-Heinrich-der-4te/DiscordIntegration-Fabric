package de.erdbeerbaerlp.dcintegration.fabric.mixin;


import de.erdbeerbaerlp.dcintegration.fabric.ConnectionEvents;
import de.erdbeerbaerlp.dcintegration.fabric.DiscordIntegrationMod;
import de.erdbeerbaerlp.dcintegration.fabric.VanishIntegration;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayNetworkHandler.class)
public class NetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    /**
     * Handle possible timeout
     */
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void onDisconnect(DisconnectionInfo info, CallbackInfo ci) {
        if (info.reason().equals(Text.translatable("disconnect.timeout")))
            DiscordIntegrationMod.timeouts.add(this.player.getUuid());
    }

    @Inject(at = @At(value = "HEAD"), method = "onDisconnected")
    private void onPlayerLeave(DisconnectionInfo info, CallbackInfo ci) {
        if (VanishIntegration.isVanished(player)) return;
        ConnectionEvents.onPlayerLeave(player);
    }
}
