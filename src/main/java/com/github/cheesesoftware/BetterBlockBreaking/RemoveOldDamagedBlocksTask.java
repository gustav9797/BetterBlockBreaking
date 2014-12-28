package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class RemoveOldDamagedBlocksTask extends BukkitRunnable {

    private final BetterBlockBreaking plugin;
    public static long millisecondsBeforeBeginFade = 120000;
    public static long millisecondsBetweenFade = 2000;
    public static int damageDecreasePerFade = 1;

    public RemoveOldDamagedBlocksTask(BetterBlockBreaking plugin) {
	this.plugin = plugin;
    }

    @Override
    public void run() {
	HashMap<Location, DamageBlock> damagedBlocks = new HashMap<Location, DamageBlock>(this.plugin.damagedBlocks);

	Iterator<Entry<Location, DamageBlock>> it = damagedBlocks.entrySet().iterator();
	while (it.hasNext()) {
	    Map.Entry<Location, DamageBlock> current = it.next();
	    Location l = current.getKey();
	    DamageBlock damageBlock = current.getValue();
	    Date dateModified = damageBlock.getDamageDate();
	    Date dateLastFade = damageBlock.getLastFadeDate();
	    boolean remove = false;

	    if (dateLastFade != null) {
		long elapsedMilliseconds = (new Date().getTime()) - dateLastFade.getTime();
		if (elapsedMilliseconds >= millisecondsBetweenFade) {
		    damageBlock.setLastFadeDate();
		    remove = this.fadeBlock(l);
		}
	    } else {
		long elapsedMilliseconds = (new Date().getTime()) - dateModified.getTime();
		if (elapsedMilliseconds >= millisecondsBeforeBeginFade) {
		    damageBlock.setLastFadeDate();
		    remove = this.fadeBlock(l);
		}
	    }

	    if (remove)
		it.remove();
	}

	this.plugin.damagedBlocks = damagedBlocks;
    }

    private boolean fadeBlock(Location l) {
	Block block = l.getBlock();
	if (block.hasMetadata("damage")) {
	    float damage = block.getMetadata("damage").get(0).asFloat();
	    damage -= damageDecreasePerFade;
	    if (damage <= 0) {
		plugin.cleanBlock(l.getBlock(), ((CraftWorld) l.getBlock().getWorld()).getHandle(), new BlockPosition(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
		return true;
	    }

	    block.setMetadata("damage", new FixedMetadataValue(this.plugin, damage));
	    if (block.hasMetadata("monsterId")) {
		WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
		BlockPosition pos = new BlockPosition(l.getBlockX(), l.getBlockY(), l.getBlockZ());
		((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
			new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, (int) damage));
	    } else
		return true;
	} else
	    return true;
	return false;
    }

}
