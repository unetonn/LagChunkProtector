package me.uneton.anarchycore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static me.uneton.anarchycore.AnarchyCore.LOGGER;

public class ChunkFreeze implements Listener {

    private final JavaPlugin plugin;
    public static final Map<Chunk, Integer> chunkPhysicsCount = new HashMap<>();
    public static final Set<Chunk> frozenChunks = new HashSet<>();
    public static final Map<Chunk, Long> chunkLagTime = new HashMap<>();
    private final long freezeThresholdNs = 900000000L;

    public ChunkFreeze(JavaPlugin plugin) {
        this.plugin = plugin;

        startTracking();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        if (isFrozen(chunk)) {
            event.setCancelled(true);
            return;
        }

        long startTime = System.nanoTime();
        int count = chunkPhysicsCount.getOrDefault(chunk, 0) + 1;
        chunkPhysicsCount.put(chunk, count);

        long elapsedTime = System.nanoTime() - startTime;
        chunkLagTime.put(chunk, chunkLagTime.getOrDefault(chunk, 0L) + elapsedTime);
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        if (isFrozen(chunk)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        if (isFrozen(chunk)) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        if (isFrozen(event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        if (isFrozen(chunk)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        chunkPhysicsCount.remove(chunk);
        frozenChunks.remove(chunk);
    }

    private void startTracking() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Chunk, Long> entry : chunkLagTime.entrySet()) {
                    Chunk chunk = entry.getKey();
                    long nanos = entry.getValue();

                    if (nanos >= freezeThresholdNs) {
                        freezeChunk(chunk);
                    }
                }
                chunkLagTime.clear();
            }
        }.runTaskTimer(plugin, 0L, 100L);
    }

    private boolean isFrozen(Chunk chunk) {
        return frozenChunks.contains(chunk);
    }

    private void freezeChunk(Chunk chunk) {
        if (!frozenChunks.contains(chunk)) {
            frozenChunks.add(chunk);
            LOGGER.warning("Frozen chunk at " + formatLocation(chunk));
        }
    }

    private String formatLocation(Chunk chunk) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16;
        int z = chunk.getZ() * 16;
        return "[" + world.getName() + " at block X=" + x + " Z=" + z + " (chunk " + chunk.getX() + ", " + chunk.getZ() + ")]";
    }
}
