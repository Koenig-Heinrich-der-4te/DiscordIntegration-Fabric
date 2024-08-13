package de.erdbeerbaerlp.dcintegration.fabric.mixin;

import com.mojang.authlib.GameProfile;
import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.compat.FloodgateUtils;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.fabric.vanish_integration.VanishIntegration;
import net.minecraft.network.ClientConnection;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.INSTANCE;


@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    /**
     * Handle whitelisting
     */
    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    public void canJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        if (DiscordIntegration.INSTANCE == null) return;
        LinkManager.checkGlobalAPI(profile.getId());
        final Component eventKick = INSTANCE.callEventO((e) -> e.onPlayerJoin(profile.getId()));
        if (eventKick != null) {
            final String jsonComp = GsonComponentSerializer.gson().serialize(eventKick).replace("\\\\n", "\n");
            try {
                final Text comp = Text.Serialization.fromJson(jsonComp, BuiltinRegistries.createWrapperLookup());
                cir.setReturnValue(comp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (Configuration.instance().linking.whitelistMode && DiscordIntegration.INSTANCE.getServerInterface().isOnlineMode()) {
            try {
                if (!LinkManager.isPlayerLinked(profile.getId())) {
                    cir.setReturnValue(Text.of(Localization.instance().linking.notWhitelistedCode.replace("%code%", "" + (FloodgateUtils.isBedrockPlayer(profile.getId()) ? LinkManager.genBedrockLinkNumber(profile.getId()) : LinkManager.genLinkNumber(profile.getId())))));
                } else if (!DiscordIntegration.INSTANCE.canPlayerJoin(profile.getId())) {
                    cir.setReturnValue(Text.of(Localization.instance().linking.notWhitelistedRole));
                }
            } catch (IllegalStateException e) {
                cir.setReturnValue(Text.of("An error occured\nPlease check Server Log for more information\n\n" + e));
                e.printStackTrace();
            }
        }
    }

    @Inject(at = @At(value = "TAIL"), method = "onPlayerConnect")
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        if (VanishIntegration.isVanished(player)) return;
        ConnectionEvents.onPlayerJoin(player);
    }
}
