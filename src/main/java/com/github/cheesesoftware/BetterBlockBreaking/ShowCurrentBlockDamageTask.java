package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.EntityPlayer;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ShowCurrentBlockDamageTask extends BukkitRunnable {

    private final Plugin plugin;
    private final Player p;
    private final BlockPosition pos;

    public ShowCurrentBlockDamageTask(Plugin plugin, Player p, BlockPosition pos) {
	this.plugin = plugin;
	this.p = p;
	this.pos = pos;
    }

    @Override
    public void run() {
	if (p.hasMetadata("BlockBeginDestroy")) {
	    Date old = (Date) p.getMetadata("BlockBeginDestroy").get(0).value();
	    Date now = new Date();
	    long differenceMilliseconds = now.getTime() - old.getTime();

	    WorldServer world = ((CraftWorld) p.getWorld()).getHandle();
	    EntityPlayer player = ((CraftPlayer) p).getHandle();
	    net.minecraft.server.v1_8_R1.Block block = world.getType(pos).getBlock();
	    Block bukkitBlock = p.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());

	    float i = differenceMilliseconds / 20;
	    float f = 1000 * ((block.getDamage(player, world, pos) * (float) (i)) / 240);
	    f += (bukkitBlock.hasMetadata("damage") ? bukkitBlock.getMetadata("damage").get(0).asFloat() : 0);
	    if (f > 10) {
		if (bukkitBlock.getType() != org.bukkit.Material.AIR && !bukkitBlock.hasMetadata("processing"))
		    ((BetterBlockBreaking) plugin).breakBlock(bukkitBlock, world, pos, p, true);
		// ((BetterBlockBreaking)plugin).cancelTask(this.getTaskId());
		this.cancel();
		return;
	    } else {
		bukkitBlock.setMetadata("damage", new FixedMetadataValue(plugin, f));
		p.setMetadata("BlockBeginDestroy", new FixedMetadataValue(plugin, new Date()));
		((BetterBlockBreaking)plugin).updateBlockInfo(bukkitBlock.getLocation(), f);
	    }

	    if (bukkitBlock.hasMetadata("monsterId")) {

		if (!bukkitBlock.hasMetadata("isNoCancel")) {
		    // Send damage packet
		    ((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(bukkitBlock.getX(), bukkitBlock.getY(), bukkitBlock.getZ(), 120, world.dimension,
			    new PacketPlayOutBlockBreakAnimation(bukkitBlock.getMetadata("monsterId").get(0).asInt(), pos, (int) f));
		}

		// Cancel old task
		if (bukkitBlock.hasMetadata("keepBlockDamageAliveTaskId")) {
		    int keepBlockDamageAliveTaskId = bukkitBlock.getMetadata("keepBlockDamageAliveTaskId").get(0).asInt();
		    Bukkit.getScheduler().cancelTask(keepBlockDamageAliveTaskId);
		}

		// Start the task which prevents block damage from disappearing
		BukkitTask aliveTask = new KeepBlockDamageAliveTask((JavaPlugin) plugin, bukkitBlock).runTaskTimer(plugin, BetterBlockBreaking.blockDamageUpdateDelay,
			BetterBlockBreaking.blockDamageUpdateDelay);
		bukkitBlock.setMetadata("keepBlockDamageAliveTaskId", new FixedMetadataValue(plugin, aliveTask.getTaskId()));
	    }
	} else
	    this.cancel();
    }

}
