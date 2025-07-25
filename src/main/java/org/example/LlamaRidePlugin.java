package org.example;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.netty.buffer.ByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LlamaRidePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Llama> playerLlamas = new HashMap<>();
    private final Map<UUID, InputState> playerInputs = new HashMap<>();

    private static class InputState {
        boolean forward;
        boolean backward;
        boolean left;
        boolean right;
    }

    @Override
    public void onLoad() {
        // Initialize PacketEvents in onLoad()
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register packet listener
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {

                if (!(event instanceof PacketPlayReceiveEvent)) return;
                PacketPlayReceiveEvent playEvent = (PacketPlayReceiveEvent) event;

                // Listen for PLAYER_INPUT packets instead
                if (!playEvent.getPacketType().equals(PacketType.Play.Client.PLAYER_INPUT)) return;

                System.out.println("Received packet: " + event.getPacketType());

                Player player = playEvent.getPlayer();
                if (!playerLlamas.containsKey(player.getUniqueId())) return;

                System.out.println("Player " + player.getName() + " is riding a llama.");

                Llama llama = playerLlamas.get(player.getUniqueId());
                if (llama == null || llama.isDead()) return;

                System.out.println("Llama found for player " + player.getName() + ": " + llama.getCustomName());

                // Check if player is actually riding the llama
                if (!llama.getPassengers().contains(player)) return;

                System.out.println("Player " + player.getName() + " is riding the llama.");

                try {
                    // Manually read packet data to avoid version issues
                    var buf = playEvent.getByteBuf();

                    System.out.println("Buffer received: " + buf);

                    ByteBuf buffer = ((ByteBuf) playEvent.getByteBuf()).duplicate();

                    System.out.println("Buffer readable bytes: " + buffer.readableBytes());
                    if (buffer.readableBytes() < 1) return; // Need at least 2 bytes

                    // Read the input flags (1 byte) and movement data
                    byte inputFlags = buffer.readByte();

                    System.out.println("Input flags: " + inputFlags);

                    // Extract movement from flags
                    boolean forward = (inputFlags & 0x01) != 0;  // W key
                    boolean backward = (inputFlags & 0x02) != 0; // S key
                    boolean left = (inputFlags & 0x04) != 0;     // A key
                    boolean right = (inputFlags & 0x08) != 0;    // D key
                    boolean jump = (inputFlags & 0x10) != 0;     // Space

                    getLogger().info("Input: F=" + forward + " B=" + backward + " L=" + left + " R=" + right + " J=" + jump);

                    InputState state = playerInputs.computeIfAbsent(player.getUniqueId(), k -> new InputState());
                    state.forward = forward;
                    state.backward = backward;
                    state.left = left;
                    state.right = right;

                    if (jump && llama.isOnGround()) {
                        llama.setVelocity(llama.getVelocity().setY(0.8));
                        player.playSound(player.getLocation(), Sound.ENTITY_LLAMA_SPIT, 0.5f, 1.5f);
                    }

                } catch (Exception e) {
                    getLogger().warning("Error processing player input: " + e.getMessage());
                }
            }
        }, PacketListenerPriority.NORMAL);

        // Initialize PacketEvents
        PacketEvents.getAPI().init();
        getLogger().info("LlamaRidePlugin enabled with PacketEvents!");

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Llama> entry : playerLlamas.entrySet()) {
                    UUID uuid = entry.getKey();
                    Llama llama = entry.getValue();
                    if (llama == null || llama.isDead()) continue;

                    InputState state = playerInputs.get(uuid);
                    if (state == null) continue;

                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !llama.getPassengers().contains(p)) continue;

                    Vector direction = p.getLocation().getDirection();
                    direction.setY(0);
                    direction.normalize();
                    Vector side = new Vector(-direction.getZ(), 0, direction.getX());
                    Vector velocity = new Vector(0, llama.getVelocity().getY(), 0);

                    if (state.forward) velocity.add(direction.multiply(0.4));
                    if (state.backward) velocity.subtract(direction.multiply(0.3));
                    if (state.left) velocity.subtract(side.multiply(0.3));
                    if (state.right) velocity.add(side.multiply(0.3));

                    llama.setVelocity(velocity);

                    // Rotate llama to match the rider's yaw
                    llama.setRotation(p.getLocation().getYaw(), 0f);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }
        for (Llama llama : playerLlamas.values()) {
            if (llama != null && !llama.isDead()) llama.remove();
        }
        playerLlamas.clear();
        playerInputs.clear();
        getLogger().info("LlamaRidePlugin disabled.");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material block = event.getClickedBlock().getType();
            if (block == Material.LANTERN || block.name().contains("LAMP")) {
                UUID uuid = player.getUniqueId();

                if (playerLlamas.containsKey(uuid)) {
                    Llama llama = playerLlamas.remove(uuid);
                    if (llama != null && !llama.isDead()) llama.remove();
                    playerInputs.remove(uuid);
                    player.sendMessage("§eZsiadłeś z lamy!");
                } else {
                    Llama llama = (Llama) player.getWorld().spawnEntity(player.getLocation(), EntityType.LLAMA);
                    llama.setAdult();
                    llama.setTamed(true);
                    llama.setOwner(player);
                    llama.setDomestication(llama.getMaxDomestication());
                    llama.setCustomName("§6Lama " + player.getName());
                    llama.setCustomNameVisible(true);

                    // Add player as passenger
                    llama.addPassenger(player);
                    playerLlamas.put(uuid, llama);
                    playerInputs.put(uuid, new InputState());
                    player.playSound(player.getLocation(), Sound.ENTITY_LLAMA_AMBIENT, 1.0f, 1.0f);
                    player.sendMessage("§aWsiadłeś na lamę! Użyj WASD i spacji.");

                    getLogger().info("Player " + player.getName() + " mounted llama");
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Llama llama = playerLlamas.get(uuid);
        if (llama == null || !event.getDismounted().equals(llama)) return;
        if (!llama.isDead()) llama.remove();
        playerLlamas.remove(uuid);
        playerInputs.remove(uuid);
        player.sendMessage("§eZsiadłeś z lamy!");
    }
}