package com.omdmrotat.pistonupdatereducer;

// Specific imports instead of org.bukkit.*
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Import ChatColor for messages
import org.bukkit.Location;
import org.bukkit.Material;
// Keep other specific imports
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
// Removed org.bukkit.block.data.Directional as it's not directly used after PistonHead import
import org.bukkit.block.data.type.Piston; // Import Piston data type
import org.bukkit.block.data.type.PistonHead; // Import PistonHead data type
import org.bukkit.command.Command; // Import Command
import org.bukkit.command.CommandExecutor; // Import CommandExecutor
import org.bukkit.command.CommandSender; // Import CommandSender
import org.bukkit.command.PluginCommand; // Import PluginCommand
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // Import EventPriority
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.plugin.java.JavaPlugin;
// No BukkitRunnable needed anymore

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set; // Use Set interface for TARGET_MATERIALS type
import java.util.EnumSet; // Use EnumSet for initialization
import java.util.logging.Level;
// No Collectors needed anymore

public final class PistonUpdateReducer extends JavaPlugin implements Listener, CommandExecutor { // Implement CommandExecutor

    // Set of materials loaded from config - Initialize as empty
    private Set<Material> targetMaterials = EnumSet.noneOf(Material.class);

    // Define default materials
    private static final Set<Material> DEFAULT_TARGET_MATERIALS = EnumSet.of(
            Material.BAMBOO,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.AIR
    );

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("PistonUpdateReducer enabling...");

        // Load configuration (generates default if needed)
        loadConfiguration();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Register command
        PluginCommand command = getCommand("pistonupdatereducer");
        if (command != null) {
            command.setExecutor(this);
        } else {
            getLogger().severe("Could not register command 'pistonupdatereducer'! It might be missing from plugin.yml.");
        }

        getLogger().info("PistonUpdateReducer enabled successfully.");
        getLogger().warning("PistonUpdateReducer is active. This plugin is EXPERIMENTAL and may break redstone contraptions by reducing block updates!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("PistonUpdateReducer disabled.");
        // Clear the set on disable (optional, good practice)
        if (targetMaterials != null) {
            targetMaterials.clear();
        }
    }

    /**
     * Loads or reloads the configuration from config.yml.
     */
    private void loadConfiguration() {
        // Save the default config.yml from the JAR if it doesn't exist
        saveDefaultConfig();
        // Reload the configuration from disk
        reloadConfig();

        // Clear the existing set before loading new values
        targetMaterials.clear();

        // Get the list of strings from the config
        List<String> materialNames = getConfig().getStringList("target-blocks");

        int loadedCount = 0;
        if (materialNames == null || materialNames.isEmpty()) {
            // If list is missing or empty, use defaults
            getLogger().info("No 'target-blocks' list found or list is empty in config.yml. Using default values (Bamboo, Sugar Cane, Cactus, Air).");
            targetMaterials.addAll(DEFAULT_TARGET_MATERIALS); // Add default materials
            loadedCount = targetMaterials.size(); // Set count to number of defaults added
        } else {
            // Otherwise, load from the list
            for (String name : materialNames) {
                if (name == null || name.trim().isEmpty()) {
                    continue; // Skip empty entries
                }
                // Use matchMaterial for flexibility (case-insensitive, handles legacy names if needed)
                Material mat = Material.matchMaterial(name.trim());
                if (mat != null) {
                    targetMaterials.add(mat);
                    loadedCount++;
                } else {
                    getLogger().warning("Invalid material name found in config.yml: '" + name + "'. Skipping.");
                }
            }
        }
        getLogger().info("Loaded " + loadedCount + " target materials."); // Log total loaded count
    }

    // --- Command Handling ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check base permission first
        if (!sender.hasPermission("pistonupdatereducer.command.base")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            // Check reload permission
            if (!sender.hasPermission("pistonupdatereducer.command.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload the configuration.");
                return true;
            }

            // Reload the configuration
            loadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "PistonUpdateReducer configuration reloaded!");
            return true;
        }

        // Show basic usage if command is incorrect
        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
        return true;
    }


    // --- Piston Event Handlers ---
    // Use a lower priority to let other plugins potentially modify/cancel first if needed
    // This synchronous handler should be executed by Folia on the correct region's thread.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // Check if the block being pushed is one of the target types
        // Cache relative block for slight optimization
        Block blockInFront = event.getBlock().getRelative(event.getDirection());
        // Use the loaded targetMaterials set
        if (targetMaterials.contains(blockInFront.getType())) {
            // If it matches, handle it with our custom logic
            handlePistonEvent(event, event.getBlocks(), event.getDirection(), true);
        }
        // If it doesn't match, do nothing and let vanilla handle it.
    }

    // This synchronous handler should be executed by Folia on the correct region's thread.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // Check if any block being pulled is one of the target types.
        List<Block> pulledBlocks = event.getBlocks();
        boolean triggerCustomLogic = false; // Renamed for clarity
        if (!pulledBlocks.isEmpty()) {
            // Check the block directly attached to the piston head
            Block directlyPulledBlock = pulledBlocks.get(pulledBlocks.size() - 1);
            // Use the loaded targetMaterials set
            if (targetMaterials.contains(directlyPulledBlock.getType())) {
                triggerCustomLogic = true;
            }
        } else {
            // Optimization: Only check blockRetractingInto if no blocks are being pulled
            // If no blocks are pulled (just the head retracting), check if the space it retracts into is AIR (or other target material)
            Block blockRetractingInto = event.getBlock().getRelative(event.getDirection());
            // Use the loaded targetMaterials set
            if (targetMaterials.contains(blockRetractingInto.getType())) {
                triggerCustomLogic = true; // Treat retracting into target material as a targeted action
            }
        }


        // If a target block condition was met, handle it with our custom logic.
        if (triggerCustomLogic) {
            handlePistonEvent(event, pulledBlocks, event.getDirection(), false);
        }
        // If no target block is being pulled and not retracting into AIR, do nothing and let vanilla handle it.
    }

    /**
     * Handles the common logic for both piston extend and retract events SYNCHRONOUSLY
     * ONLY IF the target block condition was met by the caller.
     *
     * @param event      The original BlockPistonEvent.
     * @param movedBlocks The list of blocks that would be moved by the piston.
     * @param direction  The direction the piston is facing/moving.
     * @param isExtending True if the piston is extending, false if retracting.
     */
    private void handlePistonEvent(BlockPistonEvent event, List<Block> movedBlocks, BlockFace direction, boolean isExtending) {
        Block pistonBlock = event.getBlock();
        Location pistonLoc = pistonBlock.getLocation();
        Material pistonMaterial = pistonBlock.getType(); // Get piston type (normal/sticky)

        // Basic check: Is the piston block's chunk loaded? (Should be loaded if event fired)
        if (!isLocationLoaded(pistonLoc)) {
            getLogger().warning("Piston event triggered for unloaded piston block at: " + pistonLoc);
            return; // Don't process if the piston itself isn't loaded
        }

        // --- The Core Logic: Cancel Sync, Move Sync ---

        // 1. Cancel the original event SYNCHRONOUSLY to prevent default behavior
        event.setCancelled(true);
        // getLogger().info("Cancelled piston event for target block at " + pistonLoc); // Debug log

        // Cache piston head location - calculated once
        Location pistonHeadLoc = pistonLoc.clone().add(direction.getModX(), direction.getModY(), direction.getModZ());

        // Optimization: Handle retraction with no moved blocks separately
        if (!isExtending && movedBlocks.isEmpty()) {
            // Only need to handle piston state and head removal
            updatePistonStateAndHead(pistonBlock, pistonLoc, pistonMaterial, pistonHeadLoc, direction, false);
            return; // Skip the rest of the logic for moved blocks
        }

        // 2. Prepare data for synchronous processing (only if blocks are moved)
        // Cloning is still good practice to avoid modifying original list from the event if needed later.
        final List<BlockData> originalBlockData = new ArrayList<>(movedBlocks.size()); // Initialize with capacity
        final List<Location> originalLocations = new ArrayList<>(movedBlocks.size()); // Initialize with capacity
        final List<Location> newLocations = new ArrayList<>(movedBlocks.size());      // Initialize with capacity

        for (Block block : movedBlocks) {
            Location blockLoc = block.getLocation();
            if (isLocationLoaded(blockLoc)) { // Check if source block is loaded
                originalBlockData.add(block.getBlockData().clone()); // Clone data
                originalLocations.add(blockLoc.clone());             // Clone original location
                newLocations.add(blockLoc.clone().add(direction.getModX(), direction.getModY(), direction.getModZ())); // Calculate and clone new location
            } else {
                getLogger().warning("Skipping unloaded block during piston movement preparation: " + blockLoc);
                // If a block isn't loaded, abort the custom handling. Vanilla won't run either as we cancelled.
                // This might leave things in a slightly odd state if some blocks were loaded and others weren't.
                // Consider if reverting the cancellation is better here, though complex.
                return;
            }
        }

        // 3. Perform the block manipulation synchronously
        // Folia ensures this code runs on the correct region thread because it's part of the event handler.
        try {
            // A) Clear the original locations first (in reverse order)
            // Create a mutable copy for reversing if needed, but iterating backwards might be slightly faster
            // Collections.reverse(originalLocations); // Reversing the locations list itself
            for (int i = originalLocations.size() - 1; i >= 0; i--) { // Iterate backwards
                Location loc = originalLocations.get(i);
                // Check loaded state again just before modifying
                if (isLocationLoaded(loc)) {
                    Block block = loc.getBlock(); // Cache block object
                    // Only set to AIR if it's not already AIR (minor optimization)
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false); // Set to Air, NO physics update
                    }
                } else {
                    getLogger().warning("Original block location unloaded before clearing: " + loc);
                }
            }


            // B) Place blocks in the new locations (in the original order)
            for (int i = 0; i < originalBlockData.size(); i++) {
                Location newLoc = newLocations.get(i);
                BlockData dataToPlace = originalBlockData.get(i); // Use cloned data

                if (isLocationLoaded(newLoc)) {
                    // Make sure we aren't trying to place where the head should be
                    if (!newLoc.equals(pistonHeadLoc)) {
                        Block targetBlock = newLoc.getBlock();
                        // Only place if the data is not AIR (since the target is likely already AIR from step A)
                        if (dataToPlace.getMaterial() != Material.AIR) {
                            targetBlock.setBlockData(dataToPlace, false); // Place block, NO physics update
                        }
                    } else {
                        // This case should be rare if event.getBlocks() excludes the head location, log if it happens.
                        getLogger().warning("Attempted to place moved block where piston head should be at " + newLoc);
                    }
                } else {
                    getLogger().warning("Skipping placement at unloaded location: " + newLoc);
                    // Inconsistency introduced here if target chunk unloads!
                }
            }

            // C) Manually update the piston block state and head
            // Pass cached pistonHeadLoc
            updatePistonStateAndHead(pistonBlock, pistonLoc, pistonMaterial, pistonHeadLoc, direction, isExtending);

            // Optional: Log success
            // getLogger().info("Synchronously handled piston " + (isExtending ? "extend" : "retract") + " for " + pistonLoc);

        } catch (Exception e) {
            // Log any errors occurring synchronously
            getLogger().log(Level.SEVERE, "Error during synchronous piston block manipulation for " + pistonLoc, e);
            // Consider re-throwing or handling more gracefully depending on the error.
        }
    }

    /**
     * Helper method to update the piston block's state and place/remove the piston head.
     * Extracted for clarity and reuse.
     *
     * @param pistonBlock The piston block itself.
     * @param pistonLoc The location of the piston block.
     * @param pistonMaterial The material of the piston (PISTON or STICKY_PISTON).
     * @param pistonHeadLoc The pre-calculated location where the piston head should be/was.
     * @param direction The direction the piston is facing.
     * @param isExtending The target state (true for extended, false for retracted).
     */
    private void updatePistonStateAndHead(Block pistonBlock, Location pistonLoc, Material pistonMaterial, Location pistonHeadLoc, BlockFace direction, boolean isExtending) {
        try {
            if (isLocationLoaded(pistonLoc)) { // Check piston location loaded state again
                Block currentPistonBlock = pistonBlock; // Use the event's block object (should be safe in sync event)
                if (currentPistonBlock.getType() == pistonMaterial) { // Check it's still the same piston type
                    BlockData pistonData = currentPistonBlock.getBlockData();
                    if (pistonData instanceof Piston) {
                        Piston piston = (Piston) pistonData;

                        if (piston.isExtended() != isExtending) {
                            piston.setExtended(isExtending);
                            // Apply physics for the piston itself ONLY.
                            currentPistonBlock.setBlockData(piston, true);

                            // Handle the piston head block
                            // Location headLoc = pistonHeadLoc; // Use parameter
                            if (isLocationLoaded(pistonHeadLoc)) {
                                Block headBlock = pistonHeadLoc.getBlock();
                                if (isExtending) {
                                    // Place piston head
                                    BlockData headData = Bukkit.createBlockData(Material.PISTON_HEAD);
                                    if (headData instanceof PistonHead) {
                                        PistonHead pistonHead = (PistonHead) headData;
                                        pistonHead.setFacing(direction); // Directional is implicitly handled via PistonHead
                                        pistonHead.setType(pistonMaterial == Material.STICKY_PISTON ? PistonHead.Type.STICKY : PistonHead.Type.NORMAL);
                                        pistonHead.setShort(false);
                                        headBlock.setBlockData(pistonHead, false); // NO physics update
                                    } else {
                                        getLogger().warning("Could not create PistonHead BlockData at " + pistonHeadLoc);
                                    }
                                } else {
                                    // Remove piston head if it exists
                                    if (headBlock.getType() == Material.PISTON_HEAD) {
                                        headBlock.setType(Material.AIR, false); // NO physics update
                                    }
                                }
                            } else {
                                getLogger().warning("Piston head location unloaded: " + pistonHeadLoc);
                            }
                        } // else piston state already matched, do nothing
                    } else {
                        getLogger().warning("Block at " + pistonLoc + " is no longer a Piston BlockData type!");
                    }
                } else {
                    getLogger().warning("Block at " + pistonLoc + " is no longer the expected piston material!");
                }
            } else {
                getLogger().warning("Piston location unloaded before state update: " + pistonLoc);
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error updating piston state/head for " + pistonLoc, e);
        }
    }


    /**
     * Helper method to check if a location and its chunk are loaded.
     * Should be safe to call from the main region thread during event handling.
     * @param loc The location to check.
     * @return true if the location's world and chunk are loaded, false otherwise.
     */
    private boolean isLocationLoaded(Location loc) {
        // Added null check for world which could happen if location is invalid
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        try {
            // Check chunk coordinates (blockX >> 4, blockZ >> 4)
            return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        } catch (Exception e) {
            // Catch potential exceptions (though less likely on main thread)
            getLogger().log(Level.WARNING, "Error checking if location is loaded: " + loc, e);
            return false;
        }
    }
}
