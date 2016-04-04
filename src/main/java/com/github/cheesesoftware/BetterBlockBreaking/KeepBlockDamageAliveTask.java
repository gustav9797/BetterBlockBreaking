package com.github.cheesesoftware.BetterBlockBreaking;

import net.minecraft.server.v1_9_R1.BlockPosition;
import net.minecraft.server.v1_9_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_9_R1.WorldServer;

import org.bukkit.craftbukkit.v1_9_R1.CraftServer;
import org.bukkit.craftbukkit.v1_9_R1.CraftWorld;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class KeepBlockDamageAliveTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final DamageBlock block;
    private final WorldServer world;
    private final BlockPosition pos;

    public KeepBlockDamageAliveTask(JavaPlugin plugin, DamageBlock block) {
	this.plugin = plugin;
	this.block = block;
	this.world = ((CraftWorld) block.getWorld()).getHandle();
	this.pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
    }

    @Override
    public void run() {
	if (block.isDamaged() && block.getEntity() != null && ((BetterBlockBreaking)plugin).damageBlocks.containsKey(block.getLocation())) {
	    float currentDamage = block.getDamage();

	    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(null, block.getX(), block.getY(), block.getZ(), 120, world.dimension,
		    new PacketPlayOutBlockBreakAnimation(block.getEntity().getId(), pos, (int) currentDamage));
	} else
	    this.cancel();
    }

}