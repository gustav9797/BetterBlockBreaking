package com.github.cheesesoftware.BetterBlockBreaking;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.Entity;
import net.minecraft.server.v1_8_R1.EntityChicken;
import net.minecraft.server.v1_8_R1.EnumPlayerDigType;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockChange;
import net.minecraft.server.v1_8_R1.TileEntity;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
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

    public static int blockDamageUpdateDelay = 20 * 20; // seconds * ticks
    private ProtocolManager protocolManager;
    private int removeOldDamagesBlocksTaskId = -1;

    private FileConfiguration customConfig = null;
    private File customConfigFile = null;

    public HashMap<Location, Float> damagedBlocks = new HashMap<Location, Float>();
    public HashMap<Location, Date> lastDamagedBlocks = new HashMap<Location, Date>();

    public void onEnable() {
	this.saveDefaultConfig();
	this.reloadCustomConfig();

	Bukkit.getServer().getPluginManager().registerEvents(this, this);

	BukkitTask task = new RemoveOldDamagedBlocksTask(this).runTaskTimer(this, 0, 20);
	this.removeOldDamagesBlocksTaskId = task.getTaskId();
    }

    public void onLoad() {
	protocolManager = ProtocolLibrary.getProtocolManager();
	protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.BLOCK_DIG) {
	    @Override
	    public void onPacketReceiving(PacketEvent e) {
		if (e.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {
		    final PacketContainer packet = e.getPacket();
		    final PacketEvent event = e;
		    BukkitRunnable runnable = new BukkitRunnable() {

			@Override
			public void run() {
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
				} else {
				    monsterUUID = (UUID) block.getMetadata("monster").get(0).value();
				    monsterId = block.getMetadata("monsterId").get(0).asInt();
				}

				// Set metadata to prevent duplicate animations on the same block when player doesn't stop breaking
				if (!block.hasMetadata("damage"))
				    block.setMetadata("isNoCancel", new FixedMetadataValue(plugin, true));

				// Start new task
				BukkitTask task = new ShowCurrentBlockDamageTask(plugin, p, pos).runTaskTimer(plugin, 0, 2);
				p.setMetadata("showCurrentDamageTaskId", new FixedMetadataValue(plugin, task.getTaskId()));

			    } else if (type == EnumPlayerDigType.ABORT_DESTROY_BLOCK || type == EnumPlayerDigType.STOP_DESTROY_BLOCK) {

				// Player cancelled breaking
				if (block.hasMetadata("isNoCancel") && block.hasMetadata("damage")) {

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
				    } else {
					monsterUUID = (UUID) block.getMetadata("monster").get(0).value();
					monsterId = block.getMetadata("monsterId").get(0).asInt();
				    }

				    // If it's a first time break and player stops breaking, send damage packet
				    float currentDamage = block.getMetadata("damage").get(0).asFloat();
				    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
					    new PacketPlayOutBlockBreakAnimation(monsterId, pos, (int) currentDamage));

				    // Cancel old keep-damage-alive task
				    if (block.hasMetadata("keepBlockDamageAliveTaskId")) {
					int keepBlockDamageAliveTaskId = block.getMetadata("keepBlockDamageAliveTaskId").get(0).asInt();
					Bukkit.getScheduler().cancelTask(keepBlockDamageAliveTaskId);
				    }

				    // Start the task which prevents block damage from disappearing
				    BukkitTask aliveTask = new KeepBlockDamageAliveTask((JavaPlugin) plugin, block).runTaskTimer(plugin, BetterBlockBreaking.blockDamageUpdateDelay,
					    BetterBlockBreaking.blockDamageUpdateDelay);
				    block.setMetadata("keepBlockDamageAliveTaskId", new FixedMetadataValue(plugin, aliveTask.getTaskId()));
				}
				block.removeMetadata("isNoCancel", plugin);

				// Clean old tasks
				if (p.hasMetadata("showCurrentDamageTaskId")) {
				    Bukkit.getScheduler().cancelTask(p.getMetadata("showCurrentDamageTaskId").get(0).asInt());
				    p.removeMetadata("showCurrentDamageTaskId", plugin);
				}

				// Clean metadata
				p.removeMetadata("BlockBeginDestroy", plugin);
			    }
			};
		    };
		    runnable.runTaskLater(plugin, 0);
		}
	    }
	});
    }

    public void onDisable() {
	Bukkit.getScheduler().cancelTask(this.removeOldDamagesBlocksTaskId);
    }

    public void reloadCustomConfig() {
	if (customConfigFile == null) {
	    customConfigFile = new File(getDataFolder(), "config.yml");
	}
	customConfig = YamlConfiguration.loadConfiguration(customConfigFile);
	RemoveOldDamagedBlocksTask.millisecondsForBlockRemove = customConfig.getLong("blockDamageRemovalTimeout");
	Bukkit.getLogger().log(Level.INFO, "Loaded block damage timeout: " + RemoveOldDamagedBlocksTask.millisecondsForBlockRemove);

	// Look for defaults in the jar
	Reader defConfigStream;
	try {
	    defConfigStream = new InputStreamReader(this.getResource("config.yml"), "UTF8");
	    if (defConfigStream != null) {
		YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
		customConfig.setDefaults(defConfig);
	    }
	} catch (UnsupportedEncodingException e) {
	    e.printStackTrace();
	}
    }

    public FileConfiguration getCustomConfig() {
	if (customConfig == null) {
	    reloadCustomConfig();
	}
	return customConfig;
    }

    public void saveCustomConfig() {
	if (customConfig == null || customConfigFile == null) {
	    return;
	}
	try {
	    getCustomConfig().save(customConfigFile);
	} catch (IOException ex) {
	    getLogger().log(Level.SEVERE, "Could not save config to " + customConfigFile, ex);
	}
    }

    public void saveDefaultConfig() {
	if (customConfigFile == null) {
	    customConfigFile = new File(getDataFolder(), "config.yml");
	}
	if (!customConfigFile.exists()) {
	    this.saveResource("config.yml", false);
	}
    }

    @EventHandler
    public void onPlayerDestroyBlock(BlockBreakEvent event) {
	Block block = event.getBlock();
	if (!block.hasMetadata("processing")) {
	    WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
	    BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
	    event.setCancelled(true);
	    breakBlock(event.getBlock(), world, pos, event.getPlayer(), false);
	}
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
	final List<Block> blocks = event.blockList();
	final Location explosion = event.getLocation();
	final BetterBlockBreaking plugin = this;
	final EntityExplodeEvent e = event;
	final Map<Location, Material> materials = new HashMap<Location, Material>();
	for (Block block : blocks)
	    materials.put(block.getLocation(), block.getType());

	BukkitRunnable runnable = new BukkitRunnable() {

	    @Override
	    public void run() {
		Random r = new Random();
		if (!e.isCancelled()) {
		    for (Block block : blocks) {
			double distance = explosion.distance(block.getLocation());
			Material m = materials.get(block.getLocation());
			if (m != Material.TNT) {
			    block.setType(m);
			    plugin.setBlockDamage(block, ((float) (4 + r.nextInt(8) * (1 / (distance + 0.001f)))) + (block.hasMetadata("damage") ? block.getMetadata("damage").get(0).asFloat() : 0));
			}
		    }
		}
	    }
	};
	runnable.runTaskLater(this, 1);
    }

    // API function, other plugins can set block damage
    public void setBlockDamage(Block block, float percentage) {

	WorldServer world = ((CraftWorld) block.getWorld()).getHandle();
	BlockPosition pos = new BlockPosition(block.getX(), block.getY(), block.getZ());
	// net.minecraft.server.v1_8_R1.Block nmsBlock = world.getType(pos).getBlock();

	float f = percentage * 2.4f;
	if (f > 10) {
	    if (block.getType() != org.bukkit.Material.AIR) {
		cleanBlock(block, world, pos);
		block.setType(Material.AIR);
	    }
	    return;
	} else {
	    block.setMetadata("damage", new FixedMetadataValue(this, f));
	    ((BetterBlockBreaking) this).updateBlockInfo(block.getLocation(), f);
	}

	// Load block "monster", used for displaying the damage on the block
	UUID monsterUUID;
	int monsterId;
	if (!block.hasMetadata("monster")) {
	    Entity monster = new EntityChicken(world);
	    world.addEntity(monster, SpawnReason.CUSTOM);
	    monsterUUID = monster.getUniqueID();
	    monsterId = monster.getId();
	    block.setMetadata("monster", new FixedMetadataValue(this, monsterUUID));
	    block.setMetadata("monsterId", new FixedMetadataValue(this, monsterId));
	} else {
	    monsterUUID = (UUID) block.getMetadata("monster").get(0).value();
	    monsterId = block.getMetadata("monsterId").get(0).asInt();
	}

	// Send damage packet
	((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
		new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, (int) f));

	// Cancel old task
	if (block.hasMetadata("keepBlockDamageAliveTaskId")) {
	    int keepBlockDamageAliveTaskId = block.getMetadata("keepBlockDamageAliveTaskId").get(0).asInt();
	    Bukkit.getScheduler().cancelTask(keepBlockDamageAliveTaskId);
	}

	// Start the task which prevents block damage from disappearing
	BukkitTask aliveTask = new KeepBlockDamageAliveTask(this, block).runTaskTimer(this, BetterBlockBreaking.blockDamageUpdateDelay, BetterBlockBreaking.blockDamageUpdateDelay);
	block.setMetadata("keepBlockDamageAliveTaskId", new FixedMetadataValue(this, aliveTask.getTaskId()));
    }

    public void breakBlock(Block block, WorldServer world, BlockPosition pos, Player player, boolean playSound) {
	// Don't break block if it's currently being processed
	if (block.getType() != org.bukkit.Material.AIR && !block.hasMetadata("processing")) {
	    block.setMetadata("processing", new FixedMetadataValue(this, true));

	    // Call an additional BlockBreakEvent to make sure other plugins can cancel it
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

		this.cleanBlock(block, world, pos);
	    } else {
		cleanBlock(block, world, pos);

		// Don't play sound if client broke the block entirely, creates duplicate sounds
		if (playSound) {
		    String sound = world.getType(pos).getBlock().stepSound.getBreakSound();
		    world.makeSound(pos.getX(), pos.getY(), pos.getZ(), sound, 2.0f, 1.0f);
		}

		// Use the proper function to break block, this also applies any effects the item the player is holding has on the block
		((CraftPlayer) player).getHandle().playerInteractManager.breakBlock(pos);
	    }
	    block.removeMetadata("processing", this);
	}
    }

    public void updateBlockInfo(Location l, float damage) {
	Date dateModified = new Date();
	this.damagedBlocks.put(l, damage);
	this.lastDamagedBlocks.put(l, dateModified);
    }

    public void cleanBlock(Block block, WorldServer world, BlockPosition pos) {
	// Clean tasks
	if (block.hasMetadata("showCurrentDamageTaskId")) {
	    int updateBlockDamageTaskId = block.getMetadata("showCurrentDamageTaskId").get(0).asInt();
	    Bukkit.getScheduler().cancelTask(updateBlockDamageTaskId);
	}
	if (block.hasMetadata("keepBlockDamageAliveTaskId")) {
	    int keepBlockDamageAliveTaskId = block.getMetadata("keepBlockDamageAliveTaskId").get(0).asInt();
	    Bukkit.getScheduler().cancelTask(keepBlockDamageAliveTaskId);
	}

	// Send a damage packet to remove the damage of the block
	if (block.hasMetadata("monsterId")) {
	    ((CraftServer) Bukkit.getServer()).getHandle().sendPacketNearby(block.getX(), block.getY(), block.getZ(), 120, world.dimension,
		    new PacketPlayOutBlockBreakAnimation(block.getMetadata("monsterId").get(0).asInt(), pos, -1));
	}

	// Clean monster, remove if exists
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

	// Remove all the metadata
	block.removeMetadata("damage", this);
	block.removeMetadata("monster", this);
	block.removeMetadata("monsterId", this);
	block.removeMetadata("showCurrentDamageTaskId", this);
	block.removeMetadata("keepBlockDamageAliveTaskId", this);
	block.removeMetadata("isNoCancel", this);
    }
}
