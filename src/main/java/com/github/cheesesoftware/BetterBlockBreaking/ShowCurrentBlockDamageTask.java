package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.WorldServer;

import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class ShowCurrentBlockDamageTask extends BukkitRunnable {

    private final Player p;
    private final DamageBlock damageBlock;

    public ShowCurrentBlockDamageTask(Player p, DamageBlock damageBlock) {
        this.p = p;
        this.damageBlock = damageBlock;
    }

    @Override
    public void run() {
        if (p.hasMetadata("BlockBeginDestroy")) {
            Date old = (Date) p.getMetadata("BlockBeginDestroy").get(0).value();
            Date now = new Date();
            long differenceMilliseconds = now.getTime() - old.getTime();

            WorldServer world = ((CraftWorld) p.getWorld()).getHandle();
            EntityPlayer player = ((CraftPlayer) p).getHandle();
            BlockPosition pos = new BlockPosition(damageBlock.getX(), damageBlock.getY(), damageBlock.getZ());
            IBlockData blockData = world.getType(pos);
            net.minecraft.server.v1_12_R1.Block block = blockData.getBlock();

            float i = differenceMilliseconds / 20;
            float f = 1000 * ((block.getDamage(blockData, player, world, pos) * (float) (i)) / 240);
            f += damageBlock.getDamage();
            damageBlock.setDamage(f, p);
            p.setMetadata("BlockBeginDestroy", new FixedMetadataValue(BetterBlockBreaking.getPlugin(), new Date()));
        } else
            this.cancel();
    }

}
