package de.erdbeerbaerlp.dcintegration.fabric;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.WorkThread;
import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.storage.Localization;
import de.erdbeerbaerlp.dcintegration.common.storage.linking.LinkManager;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import de.erdbeerbaerlp.dcintegration.common.util.TextColors;
import de.erdbeerbaerlp.dcintegration.fabric.util.FabricMessageUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.INSTANCE;

public class ConnectionEvents {
    public static void doNothing() {}
    public static void onPlayerLeave(ServerPlayerEntity player) {
        if (DiscordIntegrationMod.stopped) return; //Try to fix player leave messages after stop!
        if (LinkManager.isPlayerLinked(player.getUuid()) && LinkManager.getLink(null, player.getUuid()).settings.hideFromDiscord)
            return;
        INSTANCE.callEventC((a)->a.onPlayerLeave(player.getUuid()));
        final String avatarURL = Configuration.instance().webhook.playerAvatarURL.replace("%uuid%", player.getUuid().toString()).replace("%uuid_dashless%", player.getUuid().toString().replace("-", "")).replace("%name%", player.getName().getString()).replace("%randomUUID%", UUID.randomUUID().toString());
        if (DiscordIntegration.INSTANCE != null && !DiscordIntegrationMod.timeouts.contains(player.getUuid())) {
            if (!Localization.instance().playerLeave.isBlank()) {
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.playerLeaveMessages.asEmbed) {
                    if (!Configuration.instance().embedMode.playerLeaveMessages.customJSON.isBlank()) {
                        final EmbedBuilder b = Configuration.instance().embedMode.playerLeaveMessages.toEmbedJson(Configuration.instance().embedMode.playerLeaveMessages.customJSON
                                .replace("%uuid%", player.getUuid().toString())
                                .replace("%uuid_dashless%", player.getUuid().toString().replace("-", ""))
                                .replace("%name%", FabricMessageUtils.formatPlayerName(player))
                                .replace("%randomUUID%", UUID.randomUUID().toString())
                                .replace("%avatarURL%", avatarURL)
                                .replace("%playerColor%", "" + TextColors.generateFromUUID(player.getUuid()).getRGB())
                        );
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                    } else {
                        final EmbedBuilder b = Configuration.instance().embedMode.playerLeaveMessages.toEmbed().setAuthor(FabricMessageUtils.formatPlayerName(player), null, avatarURL)
                                .setDescription(Localization.instance().playerLeave.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                    }
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().playerLeave.replace("%player%", FabricMessageUtils.formatPlayerName(player)),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
            }
        } else if (DiscordIntegration.INSTANCE != null && DiscordIntegrationMod.timeouts.contains(player.getUuid())) {
            if (!Localization.instance().playerTimeout.isBlank()) {
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.playerLeaveMessages.asEmbed) {
                    final EmbedBuilder b = Configuration.instance().embedMode.playerLeaveMessages.toEmbed()
                            .setAuthor(FabricMessageUtils.formatPlayerName(player), null, avatarURL)
                            .setDescription(Localization.instance().playerTimeout.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
                    DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().playerTimeout.replace("%player%", FabricMessageUtils.formatPlayerName(player)),INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
            }
            DiscordIntegrationMod.timeouts.remove(player.getUuid());
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (DiscordIntegration.INSTANCE != null) {
            if (LinkManager.isPlayerLinked(player.getUuid()) && LinkManager.getLink(null, player.getUuid()).settings.hideFromDiscord)
                return;
            LinkManager.checkGlobalAPI(player.getUuid());
            if (!Localization.instance().playerJoin.isBlank()) {
                if (Configuration.instance().embedMode.enabled && Configuration.instance().embedMode.playerJoinMessage.asEmbed) {
                    final String avatarURL = Configuration.instance().webhook.playerAvatarURL.replace("%uuid%", player.getUuid().toString()).replace("%uuid_dashless%", player.getUuid().toString().replace("-", "")).replace("%name%", player.getName().getString()).replace("%randomUUID%", UUID.randomUUID().toString());
                    if (!Configuration.instance().embedMode.playerJoinMessage.customJSON.isBlank()) {
                        final EmbedBuilder b = Configuration.instance().embedMode.playerJoinMessage.toEmbedJson(Configuration.instance().embedMode.playerJoinMessage.customJSON
                                .replace("%uuid%", player.getUuid().toString())
                                .replace("%uuid_dashless%", player.getUuid().toString().replace("-", ""))
                                .replace("%name%", FabricMessageUtils.formatPlayerName(player))
                                .replace("%randomUUID%", UUID.randomUUID().toString())
                                .replace("%avatarURL%", avatarURL)
                                .replace("%playerColor%", "" + TextColors.generateFromUUID(player.getUuid()).getRGB())
                        );
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()));
                    } else {
                        final EmbedBuilder b = Configuration.instance().embedMode.playerJoinMessage.toEmbed();
                        b.setAuthor(FabricMessageUtils.formatPlayerName(player), null, avatarURL)
                                .setDescription(Localization.instance().playerJoin.replace("%player%", FabricMessageUtils.formatPlayerName(player)));
                        DiscordIntegration.INSTANCE.sendMessage(new DiscordMessage(b.build()), INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
                    }
                } else
                    DiscordIntegration.INSTANCE.sendMessage(Localization.instance().playerJoin.replace("%player%", FabricMessageUtils.formatPlayerName(player)), INSTANCE.getChannel(Configuration.instance().advanced.serverChannelID));
            }
            // Fix link status (if user does not have role, give the role to the user, or vice versa)
            WorkThread.executeJob(() -> {
                final UUID uuid = player.getUuid();
                if (!LinkManager.isPlayerLinked(uuid)) return;
                final Member member = DiscordIntegration.INSTANCE.getMemberById(LinkManager.getLink(null, uuid).discordID);

                if (Configuration.instance().linking.shouldNickname)
                    member.modifyNickname(FabricMessageUtils.formatPlayerName(player)).queue();

                if (Configuration.instance().linking.linkedRoleID.equals("0")) return;
                final Guild guild = DiscordIntegration.INSTANCE.getChannel().getGuild();
                final Role linkedRole = guild.getRoleById(Configuration.instance().linking.linkedRoleID);
                if (!member.getRoles().contains(linkedRole))
                    guild.addRoleToMember(member, linkedRole).queue();
            });
        }
    }
}
