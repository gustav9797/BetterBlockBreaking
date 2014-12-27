package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;
import java.util.UUID;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.Entity;
import net.minecraft.server.v1_8_R1.EntityChicken;
import net.minecraft.server.v1_8_R1.EntityPlayer;
import net.minecraft.server.v1_8_R1.EnumPlayerDigType;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockChange;
import net.minecraft.server.v1_8_R1.TileEntity;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

public class BetterBlockBreaking extends JavaPlugin implements Listener {

    private static int blockDamageUpdateDelay = 20 * 20; // seconds * ticks
    private ProtocolManager protocolManager;
    // private Queue<Integer> tasksToKill = new LinkedList<Integer>();
    private int taskKillerTaskId = -1;

    // @SuppressWarnings("deprecation")
    public void onEnable() {
	Bukkit.getServer().getPluginManager().registerEvents(this, this);
	/*
	 * BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new BukkitRunnable() {
	 * 
	 * @Override public void run() {
	 * 
	 * while(tasksToKill.size() > 0) { int current = tasksToKill.poll(); // if(Bukkit.getScheduler().isCurrentlyRunning(current) || Bukkit.getScheduler().isQueued(current)) {
	 * Bukkit.getScheduler().cancelTask(current); } //else //tasksToKill.add(current); } }}, 0, 20*5); this.taskKillerTaskId = task.getTaskId();
	 */

    }

    public void onLoad() {
	protocolManager = ProtocolLibrary.getProtocolManager();
	protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
	    // @SuppressWarnings("deprecation")
	    @Override
	    public void onPacketReceiving(PacketEvent event) {
		if (event.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {

		    PacketContainer packet = event.getPacket();
		    StructureModifier<EnumPlayerDigType> data = packet.getSpecificModifier(EnumPlayerDigType.class);
		    StructureModifier<BlockPosition> dataTemp = packet.getSpecificModifier(BlockPosition.class);

		    EnumPlayerDigType type = data.getValues().get(0);
		    Player p = event.getPlayer();
		    BlockPosition pos = dataTemp.getValues().get(0);
		    Location posLocation = new Location(p.getWorld(), pos.getX(), pos.getY(), pos.getZ());
		    Block block = posLocation.getBlock();
		    WorldServer world = ((CraftWorld) block.getWorld()).getHandle();

		    if (type == EnumPlayerDigType.START_DESTROY_BLOCK) {
			
			// Clean old task
			p.setMetadata("BlockBeginDestroy", new FixedMetadataValue(plugin, new Date()));
			if (p.hasMetadata("showCurrentDamageTaskId"))
			    Bukkit.getScheduler().cancelTask(p.getMetadata("showCurrentDamageTaskId").get(0).asInt());

			// Load block "monster", used for displaying the damage on the block
			UUID monsterUUID;
			int monsterId;
			if (!block.hasMetadata("monster")) {
			    Entity monster = new EntityChicken(world);
			    world.addEntity(monster, SpawnReason.CUSTOM);
			    monsterUUID = monster.getUniqueID();
			    monsterId = monster.getId();
			    block.setMetadata("monster", new FixedMetadataValue(plugin, monsterUUID));
			    block.setMetadata("monsterId", new FixedMetadataValue(plugin, monsterId));
			}
			else
			{
			    monsterUUID = (UUID)block.getMetadata("monster").get(0).value();
			    monsterId = block.getMetadata("monsterId").get(0).asInt();
			}

			// Start new task
			BukkitTask task = new ShowCurrentBlockDamageTask(plugin, p, pos).runTaskTimer(plugin, 0, 2);
			p.setMetadata("showCurrentDamageTaskId", new FixedMetadataValue(plugin, task.getTaskId()));
			
		    } else if (type == EnumPlayerDigType.ABORT_DESTROY_BLOCK || type == EnumPlayerDigType.STOP_DESTROY_BLOCK) {

			// Clean old task
			if (p.hasMetadata("showCurrentDamageTaskId")) {
			    Bukkit.getScheduler().cancelTask(p.getMetadata("showCurrentDamageTaskId").get(0).asInt());
			    // ((BetterBlockBreaking)plugin).cancelTask(p.getMetadata("currentDamageTaskId").get(0).asInt());
			    p.removeMetadata("showCurrentDamageTaskId", plugin);
			}
			
			//Clean metadata
			p.removeMetadata("BlockBeginDestroy", plugin);
			
			//Date old = (Date) p.getMetadata("BlockBeginDestroy").get(0).value();
			//Date now = new Date();
			//long differenceMilliseconds = now.getTime() - old.getTime();
			// if (differenceMilliseconds > 150 || (new Location(p.getWorld(), pos.getX(), pos.getY(), pos.getZ()).getBlock().hasMetadata("damage")))
			// ((BetterBlockBreaking) plugin).SetBlockDamage(p, pos, differenceMilliseconds);
		    }
		}
	    }
	});
    }

    public void onDisable() {
	Bukkit.getScheduler().cancelTask(this.taskKillerTaskId);
    }

    /*
     * public void cancelTask(int task) { this.tasksToKill.add(task); }
     */

    /*
     * public void SetBlockDamage(Player damager, BlockPosition pos, long totalMilliseconds) { WorldServer world = ((CraftWorld) damager.getWorld()).getHandle(); EntityPlayer player = ((CraftPlayer)
     * damager).getHandle(); net.minecraft.server.v1_8_R1.Block block = world.getType(pos).getBlock();
     * 
     * float i = totalMilliseconds / 20; // Magic value
     * 
     * float f = 1000 * ((block.getDamage(player, world, pos) * (float) (i)) / 240); damageBlock(damager, new Location(damager.getWorld(), pos.getX(), pos.getY(), pos.getZ()).getBlock(), f); }
     */

    @EventHandler
    public void onPlayerDestroyBlock(BlockBreakEvent event) {
	Block block = event.getBlock();
	if (!block.hasMetadata("processing")) {
	    WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
	    BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
	    cleanBlock(block, world, pos);
	    event.setCancelled(true);
	    BreakBlock(event.getBlock(), world, pos, event.getPlayer(), false);
	}
    }

    public void BreakBlock(Block block, WorldServer world, BlockPosition pos, Player player, boolean playSound) {
	if (block.getType() != org.bukkit.Material.AIR && !block.hasMetadata("processing")) {
	    block.setMetadata("processing", new FixedMetadataValue(this, true));
	    BlockBreakEvent event = new BlockBreakEvent(block, player);
	    Bukkit.getServer().getPluginManager().callEvent(event);

	    if (event.isCancelled()) {
		// Let the client know the block still exists
		((CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutBlockChange(world, pos));
		// Update any tile entity data for this block
		TileEntity tileentity = world.getTileEntity(pos);
		if (tileentity != null) {
		    ((CraftPlayer) player).getHandle().playerConnection.sendPacket(tileentity.getUpdatePacket());
		}
	    } else {
		cleanBlock(block, world, pos);
		if (playSound) {
		    String sound = world.getType(pos).getBlock().stepSound.getBreakSound();
		    world.makeSound(pos.getX(), pos.getY(), pos.getZ(), sound, 2.0f, 1.0f);
		}
		// world.triggerEffect(1012, pos, 0);

		((CraftPlayer) player).getHandle().playerInteractManager.breakBlock(pos);
	    }
	    block.removeMetadata("processing", this);
	}
    }

    private void cleanBlock(Block block, WorldServer world, BlockPosition pos) {
	if (block.hasMetadata("showCurrentDamageTaskId")) {
	    int updateBlockDamageTaskId = block.getMetadata("showCurrentDamageTaskId").get(0).asInt();
	    Bukkit.getScheduler().cancelTask(updateBlockDamageTaskId);
	    // this.cancelTask(updateBlockDamageTaskId);
	}

	// block.getWorld().playSound(block.getLocation(), Sound.ZOMBIE_WOOD, 1.0f, 1.0f);
	if (block.hasMetadata("monster")) {
	    UUID monsterUUID = (UUID) block.getMetadata("monster").get(0).value();
	    Entity toRemove = world.getEntity(monsterUUID);
	    if (block.hasMetadata("monsterId")) {
		((CraftServer) getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
			new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, -1));
	    }
	    if (toRemove != null)
		toRemove.die();
	}

	block.removeMetadata("damage", this);
	block.removeMetadata("monster", this);
	block.removeMetadata("monsterId", this);
	block.removeMetadata("showCurrentDamageTaskId", this);
    }

    /*
     * @SuppressWarnings({ "deprecation" }) private void damageBlock(Player p, Block block, float amount) { if (block != null) { WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
     * BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
     * 
     * float damage = (block.hasMetadata("damage") ? block.getMetadata("damage").get(0).asFloat() : 0); damage += amount; if (damage < 10) { block.setMetadata("damage", new FixedMetadataValue(this,
     * damage));
     * 
     * if (!block.hasMetadata("monster")) { Entity monster = new EntityChicken(world); world.addEntity(monster, SpawnReason.CUSTOM); block.setMetadata("monster", new FixedMetadataValue(this,
     * monster.getUniqueID())); block.setMetadata("monsterId", new FixedMetadataValue(this, monster.getId())); }
     * 
     * if (!block.hasMetadata("updateBlockDamageTaskId")) { BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, new ShowBlockDamageTask(this, block), blockDamageUpdateDelay,
     * blockDamageUpdateDelay); int updateBlockDamageTaskId = task.getTaskId(); block.setMetadata("updateBlockDamageTaskId", new FixedMetadataValue(this, updateBlockDamageTaskId)); }
     * 
     * ((CraftServer) getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension, new
     * PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, -1));
     * 
     * ((CraftServer) getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension, new
     * PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, (int) damage)); } else { BreakBlock(block, world, pos, p, true); } } }
     */

}
