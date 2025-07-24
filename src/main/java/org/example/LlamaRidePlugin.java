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
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
        for (Map.Entry<UUID, Llama> entry : playerLlamas.entrySet()) {
            Llama llama = entry.getValue();
            Player rider = Bukkit.getPlayer(entry.getKey());
            if (llama != null && rider != null) {
                llama.removePassenger(rider);
            }
        }
        playerLlamas.clear();
        getLogger().info("LlamaRidePlugin disabled.");
    }

    @EventHandler
    public void onLlamaInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Llama llama)) return;

        if (llama.getInventory().getDecor() == null ||
                llama.getInventory().getDecor().getType() == Material.AIR) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (playerLlamas.containsKey(uuid) && playerLlamas.get(uuid).equals(llama)) {
            llama.removePassenger(player);
            playerLlamas.remove(uuid);
            player.sendMessage("§eZsiadłeś z lamy!");
        } else {
            if (playerLlamas.containsKey(uuid)) {
                Llama previous = playerLlamas.get(uuid);
                previous.removePassenger(player);
            }
            llama.addPassenger(player);
            playerLlamas.put(uuid, llama);
            player.playSound(player.getLocation(), Sound.ENTITY_LLAMA_AMBIENT, 1.0f, 1.0f);
            player.sendMessage("§aWsiadłeś na lamę! Użyj WASD i spacji.");
        }
        event.setCancelled(true);
    }
}
