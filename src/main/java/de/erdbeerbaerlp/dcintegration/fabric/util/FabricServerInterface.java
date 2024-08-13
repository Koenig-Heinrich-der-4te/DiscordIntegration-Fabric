package de.erdbeerbaerlp.dcintegration.fabric.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dcshadow.com.vdurmont.emoji.EmojiParser;
import dcshadow.net.kyori.adventure.text.Component;
import dcshadow.net.kyori.adventure.text.TextReplacementConfig;
import dcshadow.net.kyori.adventure.text.event.ClickEvent;
import dcshadow.net.kyori.adventure.text.event.HoverEvent;
import dcshadow.net.kyori.adventure.text.format.Style;
import dcshadow.net.kyori.adventure.text.format.TextColor;
import dcshadow.net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import dcshadow.net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.ComponentUtils;
import de.erdbeerbaerlp.dcintegration.common.util.McServerInterface;
import de.erdbeerbaerlp.dcintegration.common.util.MinecraftPermission;
import de.erdbeerbaerlp.dcintegration.fabric.command.DCCommandSender;
import de.erdbeerbaerlp.dcintegration.fabric.VanishIntegration;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FabricServerInterface implements McServerInterface{
    private final MinecraftServer server;

    public FabricServerInterface(MinecraftServer minecraftServer) {

        this.server = minecraftServer;
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayerCount();
    }

    @Override
    public int getOnlinePlayers() {
        return server.getCurrentPlayerCount();
    }

    @Override
    public void sendIngameMessage(Component msg) {
        final List<ServerPlayerEntity> l = server.getPlayerManager().getPlayerList();
        try {
            for (final ServerPlayerEntity p : l) {
                if (!playerHasPermissions(p, MinecraftPermission.READ_MESSAGES, MinecraftPermission.USER))
                    return;
                if (!DiscordIntegration.INSTANCE.ignoringPlayers.contains(p.getUuid()) && !(LinkManager.isPlayerLinked(p.getUuid()) && LinkManager.getLink(null, p.getUuid()).settings.ignoreDiscordChatIngame)) {
                    final Map.Entry<Boolean, Component> ping = ComponentUtils.parsePing(msg, p.getUuid(), p.getName().getString());
                    final String jsonComp = GsonComponentSerializer.gson().serialize(ping.getValue()).replace("\\\\n", "\n");
                    final Text comp = Text.Serialization.fromJson(jsonComp, p.getWorld().getRegistryManager());
                    p.sendMessage(comp, false);
                    if (ping.getKey()) {
                        if (LinkManager.isPlayerLinked(p.getUuid())&&LinkManager.getLink(null, p.getUuid()).settings.pingSound) {
                            p.networkHandler.sendPacket(new PlaySoundS2CPacket(SoundEvents.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, p.getPos().x,p.getPos().y,p.getPos().z, 1, 1, server.getOverworld().getSeed()));
                        }
                    }
                }
            }
            //Send to server console too
            final String jsonComp = GsonComponentSerializer.gson().serialize(msg).replace("\\\\n", "\n");
            final Text comp = Text.Serialization.fromJson(jsonComp, BuiltinRegistries.createWrapperLookup());
            server.sendMessage(comp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendIngameReaction(Member member, RestAction<Message> retrieveMessage, UUID targetUUID, EmojiUnion reactionEmote) {
        final List<ServerPlayerEntity> l = server.getPlayerManager().getPlayerList();
        for (final ServerPlayerEntity p : l) {
            if (!playerHasPermissions(p, MinecraftPermission.READ_MESSAGES, MinecraftPermission.USER))
                return;
            if (p.getUuid().equals(targetUUID) && !DiscordIntegration.INSTANCE.ignoringPlayers.contains(p.getUuid()) && (LinkManager.isPlayerLinked(p.getUuid())&&!LinkManager.getLink(null, p.getUuid()).settings.ignoreDiscordChatIngame && !LinkManager.getLink(null, p.getUuid()).settings.ignoreReactions)) {

                final String emote = reactionEmote.getType() == Emoji.Type.UNICODE ? EmojiParser.parseToAliases(reactionEmote.getName()) : ":" + reactionEmote.getName() + ":";

                Style.Builder memberStyle = Style.style();
                if (Configuration.instance().messages.discordRoleColorIngame)
                    memberStyle = memberStyle.color(TextColor.color(member.getColorRaw()));

                final Component user = Component.text(member.getEffectiveName()).style(memberStyle
                        .clickEvent(ClickEvent.suggestCommand("<@" + member.getId() + ">"))
                        .hoverEvent(HoverEvent.showText(Component.text(Localization.instance().discordUserHover.replace("%user#tag%", member.getUser().getAsTag()).replace("%user%", member.getEffectiveName()).replace("%id%", member.getUser().getId())))));
                final TextReplacementConfig userReplacer = ComponentUtils.replaceLiteral("%user%", user);
                final TextReplacementConfig emoteReplacer = ComponentUtils.replaceLiteral("%emote%", emote);

                final Component out = LegacyComponentSerializer.legacySection().deserialize(Localization.instance().reactionMessage)
                        .replaceText(userReplacer).replaceText(emoteReplacer);

                if (Localization.instance().reactionMessage.contains("%msg%"))
                    retrieveMessage.submit().thenAccept((m) -> {
                        final String msg = FabricMessageUtils.formatEmoteMessage(m.getMentions().getCustomEmojis(), m.getContentDisplay());
                        final TextReplacementConfig msgReplacer = ComponentUtils.replaceLiteral("%msg%", msg);
                        sendReactionMCMessage(p, out.replaceText(msgReplacer));
                    });
                else sendReactionMCMessage(p, out);
            }
        }
    }
    private void sendReactionMCMessage(ServerPlayerEntity target, Component msgComp) {
        final String jsonComp = GsonComponentSerializer.gson().serialize(msgComp).replace("\\\\n", "\n");
        try {
            final Text comp = Text.Serialization.fromJson(jsonComp,target.getWorld().getRegistryManager());
            target.sendMessage(comp, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void runMcCommand(String cmd, CompletableFuture<InteractionHook> cmdMsg, User user) {
        final DCCommandSender s = new DCCommandSender(cmdMsg, user, server);
            try {
                server.getCommandManager().getDispatcher().execute(cmd.trim(), s);
            } catch (CommandSyntaxException e) {
                s.sendError(Text.of(e.getMessage()));
            }
    }

    @Override
    public HashMap<UUID, String> getPlayers() {
        final HashMap<UUID, String> players = new HashMap<>();
        for (final ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (VanishIntegration.isVanished(p)) continue;
            players.put(p.getUuid(), p.getDisplayName().getString().isEmpty() ? p.getName().getString() : p.getDisplayName().getString());
        }
        return players;
    }

    @Override
    public void sendIngameMessage(String msg, UUID player) {
        final ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null)
            p.sendMessage( Text.of(msg));
    }

    @Override
    public boolean isOnlineMode() {
        return Configuration.instance().bungee.isBehindBungee || server.isOnlineMode();
    }

    @Override
    public String getNameFromUUID(UUID uuid) {
        return server.getSessionService().fetchProfile(uuid,false).profile().getName();
    }

    @Override
    public String getLoaderName() {
        return "Fabric";
    }

    @Override
    public boolean playerHasPermissions(UUID player, String... permissions) {
        for (String permission : permissions) {
            for (final MinecraftPermission perm : MinecraftPermission.values()) {
                if(perm.getAsString().equals(permission)){
                    if(Permissions.check(player,perm.getAsString(), perm.getDefaultValue()).join()){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String runMCCommand(String cmd) {
        final DCCommandSender s = new DCCommandSender(server);
        try {
            server.getCommandManager().getDispatcher().execute(cmd.trim(), s);
            return s.message.toString();
        } catch (CommandSyntaxException e) {
            return e.getMessage();
        }
    }

    public boolean playerHasPermissions(PlayerEntity player, String... permissions) {
        for (String permission : permissions) {
            for (MinecraftPermission value : MinecraftPermission.values()) {
                if(value.getAsString().equals(permission)){
                    if(Permissions.check(player,value.getAsString(), value.getDefaultValue())){
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public boolean playerHasPermissions(PlayerEntity player, MinecraftPermission... permissions) {
        final String[] permissionStrings = new String[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            permissionStrings[i] = permissions[i].getAsString();
        }
        return playerHasPermissions(player, permissionStrings);
    }
}
