package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;

import net.minecraft.server.v1_10_R1.BlockPosition;
import net.minecraft.server.v1_10_R1.EntityChicken;
import net.minecraft.server.v1_10_R1.EntityLiving;
import net.minecraft.server.v1_10_R1.IBlockData;
import net.minecraft.server.v1_10_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_10_R1.PacketPlayOutBlockChange;
import net.minecraft.server.v1_10_R1.TileEntity;
import net.minecraft.server.v1_10_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_10_R1.CraftServer;
import org.bukkit.craftbukkit.v1_10_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_10_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.scheduler.BukkitTask;

public class DamageBlock {

    private Location l;
    private Date dateDamaged = null;
    private Date lastFade = null;
    private float damage = 0;
    private EntityLiving entity = null;

    public boolean isNoCancel = false;
    public int keepBlockDamageAliveTaskId = -1;

    // public int showCurrentDamageTaskId = -1;

    public DamageBlock(Location l) {
        this.l = l;
    }

    public Location getLocation() {
        return this.l;
    }

    public Date getDamageDate() {
        return this.dateDamaged;
    }

    public Date getLastFadeDate() {
        return this.lastFade;
    }

    public void resetFade() {
        this.dateDamaged = null;
        this.lastFade = null;
    }

    public EntityLiving getEntity() {
        return this.entity;
    }

    public float getDamage() {
        return this.damage;
    }

    public boolean isDamaged() {
        return this.damage > 0;
    }

    public World getWorld() {
        return this.l.getWorld();
    }

    public int getX() {
        return this.l.getBlockX();
    }

    public int getY() {
        return this.l.getBlockY();
    }

    public int getZ() {
        return this.l.getBlockZ();
    }

    private void setDamageDate() {
        this.dateDamaged = new Date();
    }

    public void setLastFadeDate() {
        this.lastFade = new Date();
    }

    public void setDamage(float damage, Player breaker) {
        this.damage = damage;
        this.setDamageDate();

        WorldServer world = ((CraftWorld) this.l.getWorld()).getHandle();
        BlockPosition pos = new BlockPosition(getX(), getY(), getZ());

        // || it is a block with no strength, break immediately
        IBlockData blockData = world.getType(pos);
        if (damage >= 10 || (damage > 0 && blockData.getBlock().b(blockData, world, pos) <= 0)) {
            this.breakBlock(breaker);
            return;
        } else {
            if (damage <= 0)
                damage = -1;

            // Load block "monster", used for displaying the damage on the block
            if (this.entity == null) {
                this.entity = new EntityChicken(world);
                world.addEntity(entity, SpawnReason.CUSTOM);
            }

            // Send damage packet
            if (!this.isNoCancel) {
                ((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(null, getX(), getY(), getZ(), 120, world.dimension,
                        new PacketPlayOutBlockBreakAnimation(entity.getId(), pos, (int) this.damage));
                // Bukkit.getLogger().info("sent damage packet " + this.damage);
            }

            // Cancel old task
            if (this.keepBlockDamageAliveTaskId != -1) {
                Bukkit.getScheduler().cancelTask(this.keepBlockDamageAliveTaskId);
            }

            // Start the task which prevents block damage from disappearing
            BukkitTask aliveTask = new KeepBlockDamageAliveTask(BetterBlockBreaking.getPlugin(), this).runTaskTimer(BetterBlockBreaking.getPlugin(), BetterBlockBreaking.blockDamageUpdateDelay,
                    BetterBlockBreaking.blockDamageUpdateDelay);
            this.keepBlockDamageAliveTaskId = aliveTask.getTaskId();
        }
    }

    public void breakBlock(Player breaker) {
        Block block = this.l.getBlock();
        if (breaker != null) {
            WorldServer world = ((CraftWorld) this.l.getWorld()).getHandle();
            BlockPosition pos = new BlockPosition(getX(), getY(), getZ());

            if (block.getType() != org.bukkit.Material.AIR) {

                // Call an additional BlockBreakEvent to make sure other plugins can cancel it
                BlockBreakEvent event = new BlockBreakEvent(block, breaker);
                Bukkit.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    // Let the client know the block still exists
                    ((CraftPlayer) breaker).getHandle().playerConnection.sendPacket(new PacketPlayOutBlockChange(world, pos));
                    // Update any tile entity data for this block
                    TileEntity tileentity = world.getTileEntity(pos);
                    if (tileentity != null) {
                        ((CraftPlayer) breaker).getHandle().playerConnection.sendPacket(tileentity.getUpdatePacket());
                    }

                    this.removeAllDamage();
                } else {
                    this.removeAllDamage();

                    // Play block break sound
                    String sound = world.getType(pos).getBlock().w().toString();
                    breaker.getWorld().playSound(new Location(breaker.getWorld(), pos.getX(), pos.getY(), pos.getZ()), sound, 2.0f, 1.0f);
                    // world.makeSound(pos.getX(), pos.getY(), pos.getZ(), sound, 2.0f, 1.0f);

                    // Use the proper function to break block, this also applies any effects the item the player is holding has on the block
                    ((CraftPlayer) breaker).getHandle().playerInteractManager.breakBlock(pos);
                }
            }
        } else {
            block.setType(Material.AIR);
            this.removeAllDamage();
        }
    }

    public void removeAllDamage() {

        WorldServer world = ((CraftWorld) this.getWorld()).getHandle();
        BlockPosition pos = new BlockPosition(this.getX(), this.getY(), this.getZ());

        // Clean tasks
        if (keepBlockDamageAliveTaskId != -1)
            Bukkit.getScheduler().cancelTask(keepBlockDamageAliveTaskId);
        // if (this.showCurrentDamageTaskId != -1)
        // Bukkit.getScheduler().cancelTask(showCurrentDamageTaskId);

        if (this.entity == null) {
            this.entity = new EntityChicken(world);
            world.addEntity(entity, SpawnReason.CUSTOM);
        }

        // Send a damage packet to remove the damage of the block
        ((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(null, this.getX(), this.getY(), this.getZ(), 120, world.dimension,
                new PacketPlayOutBlockBreakAnimation(this.getEntity().getId(), pos, -1));

        this.getEntity().die();
        BetterBlockBreaking.getPlugin().damageBlocks.remove(getLocation());
    }

    public void setEntity(EntityLiving entity) {
        this.entity = entity;
    }

}
