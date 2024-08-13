package de.erdbeerbaerlp.dcintegration.fabric;

import me.drex.vanish.api.VanishAPI;
import me.drex.vanish.api.VanishEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;

public class VanishIntegration {
    public static boolean vanishModLoaded() {return FabricLoader.getInstance().isModLoaded("melius-vanish");}
    public static void initialize() {
        if (!vanishModLoaded()) return;
        VanishEvents.VANISH_EVENT.register((player, isVanished) -> {
            if(isVanished)
                ConnectionEvents.onPlayerLeave(player);
            else
                ConnectionEvents.onPlayerJoin(player);
        });
    }

    public static boolean isVanished(Entity entity) {
        return vanishModLoaded() && VanishAPI.isVanished(entity);
    }
}
