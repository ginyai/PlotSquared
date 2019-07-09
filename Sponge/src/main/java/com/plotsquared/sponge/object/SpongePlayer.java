package com.plotsquared.sponge.object;

import com.flowpowered.math.vector.Vector3d;
import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.commands.RequiredType;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.util.PlotGameMode;
import com.intellectualcrafters.plot.util.PlotWeather;
import com.intellectualcrafters.plot.util.StringMan;
import com.plotsquared.sponge.util.SpongeUtil;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.TargetedLocationData;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.service.ban.BanService;
import org.spongepowered.api.text.chat.ChatTypes;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.World;

import java.util.Optional;
import java.util.UUID;

public class SpongePlayer extends PlotPlayer {

    public final UUID uuid;
    private String name;

    public SpongePlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        super.populatePersistentMetaMap();
    }

    public Optional<Player> getPlayer() {
        return Sponge.getServer().getPlayer(uuid);
    }
    
    @Override
    public RequiredType getSuperCaller() {
        return RequiredType.PLAYER;
    }

    @Override
    public Location getLocation() {
        Location location = super.getLocation();
        if (location == null) {
            return getPlayer().map(SpongeUtil::getLocation).orElse(new Location());
        } else {
            return location;
        }
    }
    
    @Override
    public Location getLocationFull() {
        return getPlayer().map(SpongeUtil::getLocationFull).orElse(new Location());
    }
    
    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    @Override
    public long getLastPlayed() {
        return getPlayer().map(player -> player.lastPlayed().get().toEpochMilli()).orElse(0L);
    }

    @Override
    public boolean hasPermission(String permission) {
        return getPlayer().map(player -> player.hasPermission(permission)).orElse(false);
    }

    @Override
    public boolean isPermissionSet(String permission) {
        return getPlayer()
                .map(player -> player.getPermissionValue(player.getActiveContexts(), permission) != Tristate.UNDEFINED)
                .orElse(false);
    }

    @Override
    public void sendMessage(String message) {
        if (!StringMan.isEqual(this.getMeta("lastMessage"), message) || (System.currentTimeMillis() - this.<Long>getMeta("lastMessageTime") > 5000)) {
            setMeta("lastMessage", message);
            setMeta("lastMessageTime", System.currentTimeMillis());
            getPlayer().ifPresent(player -> player.sendMessage(ChatTypes.CHAT, TextSerializers.LEGACY_FORMATTING_CODE.deserialize(message)));
        }
    }
    
    @Override
    public void teleport(Location location) {
        if ((Math.abs(location.getX()) >= 30000000) || (Math.abs(location.getZ()) >= 30000000)) {
            return;
        }
        Optional<Player> optionalPlayer = getPlayer();
        if(!optionalPlayer.isPresent()) {
            return;
        }
        Player player = optionalPlayer.get();
        String world = player.getWorld().getName();
        if (!world.equals(location.getWorld())) {
            player.transferToWorld(location.getWorld(), new Vector3d(location.getX(), location.getY(), location.getZ()));
        } else {
            org.spongepowered.api.world.Location<World> current = player.getLocation();
            current = current.setPosition(new Vector3d(location.getX(), location.getY(), location.getZ()));
            player.setLocation(current);
        }
    }
    
    @Override
    public boolean isOnline() {
        return Sponge.getServer().getPlayer(uuid).isPresent();
    }
    
    @Override
    public String getName() {
        return this.name;
    }
    
    @Override
    public void setCompassTarget(Location location) {
        Optional<Player> optionalPlayer = getPlayer();
        if(!optionalPlayer.isPresent()) {
            PS.debug("try to set compass target to an offline player.");
            return;
        }
        Optional<TargetedLocationData> target = optionalPlayer.get().getOrCreate(TargetedLocationData.class);
        if (target.isPresent()) {
            target.get().set(Keys.TARGETED_LOCATION, SpongeUtil.getLocation(location).getPosition());
        } else {
            PS.debug("Failed to set compass target.");
        }
    }

    @Override
    public void setWeather(PlotWeather weather) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("NOT IMPLEMENTED YET");
    }
    
    @Override
    public PlotGameMode getGameMode() {
        GameMode gamemode = getPlayer().get().getGameModeData().type().get();
        if (gamemode == GameModes.ADVENTURE) {
            return PlotGameMode.ADVENTURE;
        } else if (gamemode == GameModes.CREATIVE) {
            return PlotGameMode.CREATIVE;
        } else if (gamemode == GameModes.SPECTATOR) {
            return PlotGameMode.SPECTATOR;
        } else if (gamemode == GameModes.SURVIVAL) {
            return PlotGameMode.SURVIVAL;
        } else {
            return PlotGameMode.NOT_SET;
        }
    }
    
    @Override
    public void setGameMode(PlotGameMode gameMode) {
        Optional<Player> optionalPlayer = getPlayer();
        if(!optionalPlayer.isPresent()) {
            return;
        }
        Player player = optionalPlayer.get();
        switch (gameMode) {
            case ADVENTURE:
                player.offer(Keys.GAME_MODE, GameModes.ADVENTURE);
                return;
            case CREATIVE:
                player.offer(Keys.GAME_MODE, GameModes.CREATIVE);
                return;
            case SPECTATOR:
                player.offer(Keys.GAME_MODE, GameModes.SPECTATOR);
                return;
            case SURVIVAL:
                player.offer(Keys.GAME_MODE, GameModes.SURVIVAL);
                return;
            case NOT_SET:
                player.offer(Keys.GAME_MODE, GameModes.NOT_SET);
        }
    }
    
    @Override
    public void setTime(long time) {
        throw new UnsupportedOperationException("NOT IMPLEMENTED YET");
    }
    
    @Override
    public boolean getFlight() {
        Optional<Boolean> flying = getPlayer().flatMap(player -> player.get(Keys.CAN_FLY));
        return flying.isPresent() && flying.get();
    }

    @Override
    public void setFlight(boolean fly) {
        Optional<Player> optionalPlayer = getPlayer();
        if(!optionalPlayer.isPresent()) {
            return;
        }
        Player player = optionalPlayer.get();
        player.offer(Keys.IS_FLYING, fly);
        player.offer(Keys.CAN_FLY, fly);
    }

    @Override
    public void playMusic(Location location, int id) {
        Optional<Player> optionalPlayer = getPlayer();
        if(!optionalPlayer.isPresent()) {
            return;
        }
        Player player = optionalPlayer.get();
        switch (id) {
            case 0:
                //Placeholder because Sponge doesn't have a stopSound() implemented yet.
                player.playSound(SoundTypes.BLOCK_CLOTH_PLACE, SpongeUtil.getLocation(location).getPosition(), 0);
                break;
            case 2256:
                player.playSound(SoundTypes.RECORD_11, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2257:
                player.playSound(SoundTypes.RECORD_13, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2258:
                player.playSound(SoundTypes.RECORD_BLOCKS, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2259:
                player.playSound(SoundTypes.RECORD_CAT, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2260:
                player.playSound(SoundTypes.RECORD_CHIRP, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2261:
                player.playSound(SoundTypes.RECORD_FAR, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2262:
                player.playSound(SoundTypes.RECORD_MALL, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2263:
                player.playSound(SoundTypes.RECORD_MELLOHI, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2264:
                player.playSound(SoundTypes.RECORD_STAL, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2265:
                player.playSound(SoundTypes.RECORD_STRAD, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2266:
                player.playSound(SoundTypes.RECORD_WAIT, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
            case 2267:
                player.playSound(SoundTypes.RECORD_WARD, SpongeUtil.getLocation(location).getPosition(), 1);
                break;
        }
    }
    
    @Override
    public void kick(String message) {
        getPlayer().ifPresent(player -> player.kick(SpongeUtil.getText(message)));
    }

    @Override public void stopSpectating() {
        //Not Implemented
    }

    @Override
    public boolean isBanned() {
        Optional<BanService> service = Sponge.getServiceManager().provide(BanService.class);
        Optional<GameProfile> optionalGameProfile = Sponge.getServer().getGameProfileManager().getCache().getById(uuid);
        return optionalGameProfile.filter(gameProfile -> service.isPresent() && service.get().isBanned(gameProfile)).isPresent();
    }
}
