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
	// int i = (int) ((float) currentDamage / 240.0f * 10.0F);
	if (block.hasMetadata("monsterId")) {
	    int monsterId = block.getMetadata("monsterId").get(0).asInt();

	    int dimension = world.dimension;
	    // ((CraftServer)
	    // plugin.getServer()).getHandle().sendPacketNearby(block.getX(),
	    // block.getY(), block.getZ(), 120, dimension,
	    // new PacketPlayOutBlockBreakAnimation(monsterId, pos, 0));
	    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, dimension,
		    new PacketPlayOutBlockBreakAnimation(monsterId, pos, (int) currentDamage));
	}
	else
	    this.cancel();
    }

}
