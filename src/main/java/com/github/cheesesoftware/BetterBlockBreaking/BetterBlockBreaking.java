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
import java.util.logging.Level;

import net.minecraft.server.v1_8_R1.BlockPosition;
import net.minecraft.server.v1_8_R1.EntityChicken;
import net.minecraft.server.v1_8_R1.EntityLiving;
import net.minecraft.server.v1_8_R1.EnumPlayerDigType;
import net.minecraft.server.v1_8_R1.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R1.WorldServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R1.CraftServer;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
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
    private boolean useCustomExplosions = true;

    private FileConfiguration customConfig = null;
    private File customConfigFile = null;

    public HashMap<Location, DamageBlock> damageBlocks = new HashMap<Location, DamageBlock>();

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
		    final HashMap<Location, DamageBlock> damageBlocks = ((BetterBlockBreaking) plugin).damageBlocks;
		    BukkitRunnable runnable = new BukkitRunnable() {

			@Override
			public void run() {
			    StructureModifier<EnumPlayerDigType> data = packet.getSpecificModifier(EnumPlayerDigType.class);
			    StructureModifier<BlockPosition> dataTemp = packet.getSpecificModifier(BlockPosition.class);

			    EnumPlayerDigType type = data.getValues().get(0);
			    Player p = event.getPlayer();
			    BlockPosition pos = dataTemp.getValues().get(0);
			    Location posLocation = new Location(p.getWorld(), pos.getX(), pos.getY(), pos.getZ());

			    DamageBlock damageBlock = damageBlocks.get(posLocation);
			    if (damageBlock == null) {
				damageBlock = new DamageBlock(posLocation);
				damageBlocks.put(posLocation, damageBlock);
			    }

			    if (type == EnumPlayerDigType.START_DESTROY_BLOCK) {
				// Clean old task
				p.setMetadata("BlockBeginDestroy", new FixedMetadataValue(plugin, new Date()));
				if (damageBlock.showCurrentDamageTaskId != -1) {
				    Bukkit.getScheduler().cancelTask(damageBlock.showCurrentDamageTaskId);
				    damageBlock.showCurrentDamageTaskId = -1;
				}

				// Prevent duplicate animations on the same block when player doesn't stop breaking
				if (!damageBlock.isDamaged())
				    damageBlock.isNoCancel = true;

				// Start new task
				BukkitTask task = new ShowCurrentBlockDamageTask(p, damageBlock).runTaskTimer(plugin, 0, 2);
				damageBlock.showCurrentDamageTaskId = task.getTaskId();

			    } else if (type == EnumPlayerDigType.ABORT_DESTROY_BLOCK || type == EnumPlayerDigType.STOP_DESTROY_BLOCK) {

				// Player cancelled breaking
				if (damageBlock.isNoCancel && damageBlock.isDamaged()) {

				    // Load block "monster", used for displaying the damage on the block
				    WorldServer world = ((CraftWorld) damageBlock.getWorld()).getHandle();
				    EntityLiving entity = damageBlock.getEntity();
				    if (entity == null) {
					entity = new EntityChicken(world);
					world.addEntity(entity, SpawnReason.CUSTOM);
					damageBlock.setEntity(entity);
				    }

				    // Send damage packet
				    float currentDamage = damageBlock.getDamage();
				    ((CraftServer) plugin.getServer()).getHandle().sendPacketNearby(posLocation.getX(), posLocation.getY(), posLocation.getZ(), 120, world.dimension,
					    new PacketPlayOutBlockBreakAnimation(damageBlock.getEntity().getId(), pos, (int) currentDamage));

				    // Cancel old keep-damage-alive task
				    if (damageBlock.keepBlockDamageAliveTaskId != -1) {
					Bukkit.getScheduler().cancelTask(damageBlock.keepBlockDamageAliveTaskId);
					damageBlock.keepBlockDamageAliveTaskId = -1;
				    }

				    // Start the task which prevents block damage from disappearing
				    BukkitTask aliveTask = new KeepBlockDamageAliveTask((JavaPlugin) plugin, damageBlock).runTaskTimer(plugin, BetterBlockBreaking.blockDamageUpdateDelay,
					    BetterBlockBreaking.blockDamageUpdateDelay);
				    damageBlock.keepBlockDamageAliveTaskId = aliveTask.getTaskId();
				}
				damageBlock.isNoCancel = false;

				// Clean old tasks
				if (damageBlock.showCurrentDamageTaskId != -1) {
				    Bukkit.getScheduler().cancelTask(damageBlock.showCurrentDamageTaskId);
				    damageBlock.showCurrentDamageTaskId = -1;
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

	if (customConfig.contains("millisecondsBeforeBeginFade"))
	    RemoveOldDamagedBlocksTask.millisecondsBeforeBeginFade = customConfig.getLong("millisecondsBeforeBeginFade");

	if (customConfig.contains("millisecondsBetweenFade"))
	    RemoveOldDamagedBlocksTask.millisecondsBetweenFade = customConfig.getLong("millisecondsBetweenFade");

	if (customConfig.contains("damageDecreasePerFade"))
	    RemoveOldDamagedBlocksTask.damageDecreasePerFade = customConfig.getInt("damageDecreasePerFade");

	if (customConfig.contains("useCustomExplosions"))
	    this.useCustomExplosions = customConfig.getBoolean("useCustomExplosions");

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
	this.getDamageBlock(block.getLocation()).removeAllDamage();
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
	if (this.useCustomExplosions) {
	    final List<Block> blocks = event.blockList();
	    final Location explosion = event.getLocation();
	    final EntityExplodeEvent e = event;
	    final Map<Location, Material> materials = new HashMap<Location, Material>();
	    for (Block block : blocks)
		materials.put(block.getLocation(), block.getType());
	    event.setYield(0);

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
				DamageBlock damageBlock = getDamageBlock(block.getLocation());
				damageBlock.setDamage((float) ((14 + (r.nextInt(5) - 2) - (2.0f * distance)) + damageBlock.getDamage()), null);
			    }
			}
		    }
		}
	    };
	    runnable.runTaskLater(this, 0);
	}
    }

    public DamageBlock getDamageBlock(Location location) {
	DamageBlock damageBlock = this.damageBlocks.get(location);
	if (damageBlock == null) {
	    damageBlock = new DamageBlock(location);
	    damageBlocks.put(location, damageBlock);
	}
	return damageBlock;
    }

    public static BetterBlockBreaking getPlugin() {
	return (BetterBlockBreaking) Bukkit.getPluginManager().getPlugin("BetterBlockBreaking");
    }
}
