package dev.coffeese.linkbarrel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
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

import dev.coffeese.linkbarrel.Containers.ContainerData;

public class ChestListener implements Listener {

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, ContainerData> caches = new HashMap<>();
    private final Containers containers;

    public ChestListener(Plugin plugin, Containers containers, Logger logger) {
        this.plugin = plugin;
        this.containers = containers;
        this.logger = logger;

        init();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(final PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (player.isSneaking())
            return;

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (!e.hasBlock())
            return;

        Block block = e.getClickedBlock();
        if (block.getType() != Material.BARREL)
            return;

        Barrel proxy = (Barrel)block.getState();
        String realName = proxy.getCustomName();
        if (realName == null)
            return;

        if (!realName.startsWith("##"))
            return;

        e.setCancelled(true);

        String name = realName.substring(2);
        if (caches.containsKey(name)) {
            ContainerData data = caches.get(name);
            Barrel barrel = findBarrel(data);
            if (barrel != null) {
                player.openInventory(barrel.getInventory());
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.5f, 1);
            } else {
                player.sendMessage("[LinkBarrel]" + ChatColor.RED + " Not found parent container!");
                caches.remove(name);
                return;
            }
        } else {
            player.sendMessage("[LinkBarrel]" + ChatColor.RED + " Cannot open parent container!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        if (block.getType() != Material.BARREL)
            return;

        Barrel barrel = (Barrel)block.getState();
        String realName = barrel.getCustomName();
        if (realName == null)
            return;

        if (!realName.startsWith("@@"))
            return;

        Player player = e.getPlayer();
        String name = realName.substring(2);
        if (caches.containsKey(name)) {
            player.sendMessage("[LinkBarrel]" + ChatColor.RED + " " + name + " is already exists!");
            return;
        }

        ContainerData data = containers.addContainer(name, barrel);
        if (data != null) {
            player.sendMessage("[LinkBarrel]" + ChatColor.GREEN + " " + name + " is ready!");
            caches.put(name, data);
        } else {
            player.sendMessage("[LinkBarrel]" + ChatColor.RED + " " + name + " error!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.BARREL)
            return;

        Barrel barrel = (Barrel)block.getState();
        String realName = barrel.getCustomName();
        if (barrel.getCustomName() == null)
            return;

        if (!realName.startsWith("@@"))
            return;

        String name = realName.substring(2);
        if (!caches.containsKey(name))
            return;

        ContainerData data = caches.get(name);
        if (barrel.getX() != data.x)
            return;

        if (barrel.getY() != data.y)
            return;

        if (barrel.getZ() != data.z)
            return;

        Player player = e.getPlayer();
        if (player != null) {
            player.sendMessage("[LinkBarrel]" + ChatColor.GREEN + " " + name + " was broken!");
        }

        if (!containers.removeContainer(realName)) {
            if (player != null) {
                player.sendMessage("[LinkBarrel]" + ChatColor.RED + " " + name + " broken error!");
            }
        }
        caches.remove(name);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(final InventoryMoveItemEvent e) {
        Inventory initiator = e.getInitiator();
        if (initiator.getType() != InventoryType.HOPPER)
            return;

        Inventory destination = e.getDestination();
        if (destination.getType() != InventoryType.BARREL)
            return;

        Barrel proxy = (Barrel)destination.getHolder();
        String realName = proxy.getCustomName();
        if (realName == null)
            return;

        if (!realName.startsWith("##"))
            return;

        e.setCancelled(true);

        String name = realName.substring(2);
        if (!caches.containsKey(name))
            return;

        ItemStack stack = e.getItem();
        Inventory source = e.getSource();
        ContainerData data = caches.get(name);
        Barrel barrel = findBarrel(data);
        if (barrel == null)
         return;

        Inventory target = barrel.getInventory();
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

                HashMap<Integer, ItemStack> lefts = target.addItem(destinationStack);
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
            Barrel barrel = findBarrel(data);
            if (barrel == null) {
                logger.warning(data.name + " is not found.");
                containers.removeContainer(data.name);
                continue;
            }

            String realName = barrel.getCustomName();
            if (realName == null) {
                logger.warning(data.name + " is not named.");
                containers.removeContainer(data.name);
                continue;
            }

            String name = realName.substring(2);
            if (!name.equals(data.name)) {
                logger.warning(data.name + " is invalid name, actual " + realName + ".");
                containers.removeContainer(data.name);
                continue;
            }

            caches.put(name, data);
        }
    }

    @Nullable
    private Barrel findBarrel(ContainerData data) {
        UUID uuid = UUID.fromString(data.uuid);
        World world = Bukkit.getWorld(uuid);
        if (world == null) {
            logger.warning(data.name + " is located unknown world.");
            return null;
        }

        Block block = world.getBlockAt(data.x, data.y, data.z);
        if (block == null) {
            logger.warning(data.name + " is not located.");
            return null;
        }

        if (block.getType() != Material.BARREL) {
            logger.warning(data.name + " is not barrel.");
            return null;
        }

        Barrel barrel = (Barrel)block.getState();
        return barrel;
    }
}
