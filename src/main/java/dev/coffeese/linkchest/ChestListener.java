package dev.coffeese.linkchest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import dev.coffeese.linkchest.Containers.ContainerData;

public class ChestListener implements Listener {

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, Barrel> caches = new HashMap<>();
    private final Containers containers;

    public ChestListener(Plugin plugin, Containers containers, Logger logger) {
        this.plugin = plugin;
        this.containers = containers;
        this.logger = logger;

        init();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(final PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (!e.hasBlock())
            return;

        Block block = e.getClickedBlock();
        if (block.getType() != Material.CHEST)
            return;

        Chest chest = (Chest)block.getState();
        if (chest.getCustomName() == null)
            return;

        String name = chest.getCustomName();
        if (!name.startsWith("@@"))
            return;

        e.setCancelled(true);

        Player player = e.getPlayer();
        if (caches.containsKey(name)) {
            Barrel barrel = caches.get(name);
            player.openInventory(barrel.getInventory());
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1, 1);
        } else {
            player.sendMessage("[LinkChest]" + ChatColor.RED + " Cannot open parent container!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        if (block.getType() != Material.BARREL)
            return;

        Barrel barrel = (Barrel)block.getState();
        if (barrel.getCustomName() == null)
            return;

        String name = barrel.getCustomName();
        if (!name.startsWith("@@"))
            return;

        Player player = e.getPlayer();
        if (caches.containsKey(name)) {
            player.sendMessage("[LinkChest]" + ChatColor.RED + " " + name + " is already exists!");
            return;
        }

        if (containers.addContainer(name, barrel)) {
            player.sendMessage("[LinkChest]" + ChatColor.GREEN + " " + name + " is ready!");
            caches.put(name, barrel);
        } else {
            player.sendMessage("[LinkChest]" + ChatColor.RED + " " + name + " error!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.BARREL)
            return;

        Barrel barrel = (Barrel)block.getState();
        if (barrel.getCustomName() == null)
            return;

        String name = barrel.getCustomName();
        if (!name.startsWith("@@"))
            return;

        if (!caches.containsKey(name))
            return;

        Barrel target = caches.get(name);
        if (barrel.getX() != target.getX())
            return;

        if (barrel.getY() != target.getY())
            return;

        if (barrel.getZ() != target.getZ())
            return;

        Player player = e.getPlayer();
        if (player != null) {
            player.sendMessage("[LinkChest]" + ChatColor.GREEN + " " + name + " was broken!");
        }

        if (!containers.removeContainer(name)) {
            player.sendMessage("[LinkChest]" + ChatColor.RED + " " + name + " broken error!");
        }
        caches.remove(name);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(final InventoryMoveItemEvent e) {
        Inventory initiator = e.getInitiator();
        if (initiator.getType() != InventoryType.HOPPER)
            return;

        Inventory destination = e.getDestination();
        if (destination.getType() != InventoryType.CHEST)
            return;

        if (destination.getSize() != 27)
            return;

        Chest chest = (Chest)destination.getHolder();
        if (chest.getCustomName() == null)
            return;

        String name = chest.getCustomName();
        if (!name.startsWith("@@"))
            return;

        e.setCancelled(true);

        if (!caches.containsKey(name))
            return;

        ItemStack stack = e.getItem();
        Inventory source = e.getSource();
        Inventory barrel = caches.get(name).getInventory();
        int amount = stack.getAmount();

        new BukkitRunnable() {
            @Override
            public void run() {
                int index = source.first(stack.getType());
                if (index < 0)
                    return;

                ItemStack sourceStack = source.getItem(index);
                ItemStack destinationStack = sourceStack.clone();
                sourceStack.setAmount(sourceStack.getAmount() - amount);
                destinationStack.setAmount(amount);

                HashMap<Integer, ItemStack> lefts = barrel.addItem(destinationStack);
                for (ItemStack left : lefts.values())
                    source.addItem(left);
            }
        }.runTaskLater(this.plugin, 1);
    }

    private void init() {
        List<ContainerData> list = containers.loadContainers();
        if (list == null) {
            logger.warning("Load container failed...");
            return;
        }

        for (ContainerData data : list) {
            UUID uuid = UUID.fromString(data.uuid);
            World world = Bukkit.getWorld(uuid);
            if (world == null) {
                logger.warning(data.name + " is located unknown world.");
                containers.removeContainer(data.name);
                continue;
            }

            Block block = world.getBlockAt(data.x, data.y, data.z);
            if (block == null) {
                logger.warning(data.name + " is not found.");
                containers.removeContainer(data.name);
                continue;
            }

            if (block.getType() != Material.BARREL) {
                logger.warning(data.name + " is not barrel.");
                containers.removeContainer(data.name);
                continue;
            }

            Barrel barrel = (Barrel)block.getState();
            String name = barrel.getCustomName();
            if (name == null) {
                logger.warning(data.name + " is not named.");
                containers.removeContainer(data.name);
                continue;
            }

            if (!name.equals(data.name)) {
                logger.warning(data.name + " is invalid name, actual " + name + ".");
                containers.removeContainer(data.name);
                continue;
            }

            caches.put(name, barrel);
        }
    }
}
