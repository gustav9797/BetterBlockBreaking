package com.github.cheesesoftware.BetterBlockBreaking;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class ShowBlockDamageTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final Block block;
    private final WorldServer world;
    private final BlockPosition pos;

    public ShowBlockDamageTask(JavaPlugin plugin, Block block) {
	this.plugin = plugin;
	this.block = block;
	this.world = ((CraftWorld) block.getWorld()).getHandle();
	this.pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    @Override
    public void run() {
	float currentDamage = block.getMetadata("damage").get(0).asFloat();
	if (block.hasMetadata("monsterId")) {
	    int monsterId = block.getMetadata("monsterId").get(0).asInt();

	    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
		    new PacketPlayOutBlockBreakAnimation(monsterId, pos, (int) currentDamage));
	} else
	    this.cancel();
    }

}
