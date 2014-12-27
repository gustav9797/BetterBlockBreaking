package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.server.v1_8_R1.BlockPosition;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.scheduler.BukkitRunnable;

public class RemoveOldDamagedBlocksTask extends BukkitRunnable {

    private final BetterBlockBreaking plugin;
    private static long millisecondsForBlockRemove = 1000 * 120;

    public RemoveOldDamagedBlocksTask(BetterBlockBreaking plugin) {
	this.plugin = plugin;
    }

    @Override
    public void run() {
	HashMap<Location, Float> damagedBlocks = new HashMap<Location, Float>(this.plugin.damagedBlocks);
	HashMap<Location, Date> lastDamagedBlocks = new HashMap<Location, Date>(this.plugin.lastDamagedBlocks);
	
	Iterator<Entry<Location, Float>> it = damagedBlocks.entrySet().iterator();
	while(it.hasNext())
	{
	    Map.Entry<Location, Float> current = it.next();
	    Location l = current.getKey();
	    //float damage = current.getValue();	//damage for future use
	    Date dateModified = lastDamagedBlocks.get(l);
	    long elapsedMilliseconds = (new Date().getTime()) - dateModified.getTime();
	    if(elapsedMilliseconds > millisecondsForBlockRemove)
	    {
		plugin.cleanBlock(l.getBlock(), ((CraftWorld)l.getBlock().getWorld()).getHandle(), new BlockPosition(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
		lastDamagedBlocks.remove(l);
		it.remove();
	    }
	}
	
	this.plugin.damagedBlocks = damagedBlocks;
	this.plugin.lastDamagedBlocks = lastDamagedBlocks;
    }

}
