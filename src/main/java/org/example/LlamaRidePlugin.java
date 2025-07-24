package org.example;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LlamaRidePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Llama> playerLlamas = new HashMap<>();

    @Override
    public void onEnable() {

        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (!(event instanceof PacketPlayReceiveEvent)) return;
                PacketPlayReceiveEvent playEvent = (PacketPlayReceiveEvent) event;
                if (!playEvent.getPacketType().equals(PacketType.Play.Client.STEER_VEHICLE)) return;
                Player player = playEvent.getPlayer();
                if (!playerLlamas.containsKey(player.getUniqueId())) return;

                Llama llama = playerLlamas.get(player.getUniqueId());
                if (llama == null || llama.isDead()) return;

                WrapperPlayClientSteerVehicle packet = new WrapperPlayClientSteerVehicle(event);
                float forward = packet.getForward();
                float sideways = packet.getSideways();
                boolean jump = packet.isJump();

                Vector direction = player.getLocation().getDirection();
                direction.setY(0);
                direction.normalize();

                Vector side = new Vector(-direction.getZ(), 0, direction.getX());

                Vector velocity = direction.multiply(forward * 0.4).add(side.multiply(sideways * 0.3));
                velocity.setY(llama.getVelocity().getY());
                llama.setVelocity(velocity);

                if (jump && llama.isOnGround()) {
                    llama.setVelocity(llama.getVelocity().setY(0.8));
                    player.playSound(player.getLocation(), Sound.ENTITY_LLAMA_SPIT, 0.5f, 1.5f);
                }
            }
        }, PacketListenerPriority.NORMAL);

        PacketEvents.getAPI().init();
        getLogger().info("LlamaRidePlugin enabled with PacketEvents!");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        for (Llama llama : playerLlamas.values()) {
            if (llama != null && !llama.isDead()) llama.remove();
        }
        playerLlamas.clear();
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
                    player.sendMessage("§eZsiadłeś z lamy!");
                } else {
                    Llama llama = (Llama) player.getWorld().spawnEntity(player.getLocation(), EntityType.LLAMA);
                    llama.setAdult();
                    llama.setTamed(true);
                    llama.setOwner(player);
                    llama.setCustomName("§6Lama " + player.getName());
                    llama.setCustomNameVisible(true);
                    llama.addPassenger(player);
                    playerLlamas.put(uuid, llama);
                    player.playSound(player.getLocation(), Sound.ENTITY_LLAMA_AMBIENT, 1.0f, 1.0f);
                    player.sendMessage("§aWsiadłeś na lamę! Użyj WASD i spacji.");
                }
                event.setCancelled(true);
            }
        }
    }
}
