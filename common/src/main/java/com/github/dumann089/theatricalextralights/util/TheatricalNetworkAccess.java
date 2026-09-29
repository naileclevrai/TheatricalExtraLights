package com.github.dumann089.theatricalextralights.util;

import dev.imabad.theatrical.util.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Accès runtime aux réseaux Theatrical (package non exposé sur le classpath common).
 */
public final class TheatricalNetworkAccess {

    private TheatricalNetworkAccess() {
    }

    public static boolean canPlayerConfigure(Player player, Level level, UUID networkId) {
        if (networkId.equals(UUIDUtil.NULL) || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        try {
            Object networkData = Class.forName("dev.imabad.theatrical.networks.TheatricalNetworkData")
                    .getMethod("getInstance", ServerLevel.class)
                    .invoke(null, serverLevel);
            Object network = networkData.getClass().getMethod("getNetwork", UUID.class).invoke(networkData, networkId);
            if (network == null) {
                return true;
            }
            Object members = network.getClass().getMethod("members").invoke(network);
            return (boolean) members.getClass().getMethod("isMember", UUID.class).invoke(members, player.getUUID());
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    public static String getNetworkName(Level level, UUID networkId) {
        if (networkId.equals(UUIDUtil.NULL)) {
            return "—";
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return networkId.toString();
        }
        try {
            Object networkData = Class.forName("dev.imabad.theatrical.networks.TheatricalNetworkData")
                    .getMethod("getInstance", ServerLevel.class)
                    .invoke(null, serverLevel);
            Object network = networkData.getClass().getMethod("getNetwork", UUID.class).invoke(networkData, networkId);
            if (network == null) {
                return "Unknown";
            }
            return (String) network.getClass().getMethod("name").invoke(network);
        } catch (ReflectiveOperationException ignored) {
            return networkId.toString();
        }
    }
}
