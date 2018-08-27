package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.server.v1_13_R1.BlockPosition;
import net.minecraft.server.v1_13_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_13_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_13_R1.CraftServer;
import org.bukkit.craftbukkit.v1_13_R1.CraftWorld;
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
	HashMap<Location, DamageBlock> damagedBlocks = new HashMap<Location, DamageBlock>(this.plugin.damageBlocks);

	Iterator<Entry<Location, DamageBlock>> it = damagedBlocks.entrySet().iterator();
	while (it.hasNext()) {
	    Map.Entry<Location, DamageBlock> current = it.next();
	    DamageBlock damageBlock = current.getValue();
	    Date dateModified = damageBlock.getDamageDate();
	    Date dateLastFade = damageBlock.getLastFadeDate();
	    boolean remove = false;

	    if (dateLastFade != null) {
		long elapsedMilliseconds = (new Date().getTime()) - dateLastFade.getTime();
		if (elapsedMilliseconds >= millisecondsBetweenFade) {
		    damageBlock.setLastFadeDate();
		    remove = this.fadeBlock(damageBlock);
		}
	    } else if (dateModified != null) {
		long elapsedMilliseconds = (new Date().getTime()) - dateModified.getTime();
		if (elapsedMilliseconds >= millisecondsBeforeBeginFade) {
		    damageBlock.setLastFadeDate();
		    remove = this.fadeBlock(damageBlock);
		}
	    } else
		remove = true;

	    if (remove)
		it.remove();
	}

	this.plugin.damageBlocks = damagedBlocks;
    }

    private boolean fadeBlock(DamageBlock damageBlock) {
	if (damageBlock.isDamaged()) {
	    BlockPosition pos = new BlockPosition(damageBlock.getX(), damageBlock.getY(), damageBlock.getZ());
	    float damage = damageBlock.getDamage();
	    damage -= damageDecreasePerFade;
	    if (damage <= 0) {
		damageBlock.removeAllDamage();
		return true;
	    }

	    damageBlock.setDamage(damage, null);
	    if (damageBlock.getEntity() != null) {
		WorldServer world = ((CraftWorld) damageBlock.getWorld()).getHandle();

		((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(null, damageBlock.getX(), damageBlock.getY(), damageBlock.getZ(), 120, world.dimension,
			new PacketPlayOutBlockBreakAnimation(damageBlock.getEntity().getId(), pos, (int) damage));
	    } else
		return true;
	} else
	    return true;
	return false;
    }

}
