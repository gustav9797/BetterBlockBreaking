package com.github.cheesesoftware.BetterBlockBreaking;

import net.minecraft.server.v1_8_R2.BlockPosition;
import net.minecraft.server.v1_8_R2.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R2.WorldServer;

import org.bukkit.craftbukkit.v1_8_R2.CraftServer;
import org.bukkit.craftbukkit.v1_8_R2.CraftWorld;
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
	if (block.isDamaged() && block.getEntityId() != -1) {
	    float currentDamage = block.getDamage();

	    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
		    new PacketPlayOutBlockBreakAnimation(block.getEntityId(), pos, (int) currentDamage));
	} else
	    this.cancel();
    }

}