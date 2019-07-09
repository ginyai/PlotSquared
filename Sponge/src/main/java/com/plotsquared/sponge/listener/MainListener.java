package com.plotsquared.sponge.listener;

import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.config.Settings;
import com.intellectualcrafters.plot.flag.Flags;
import com.intellectualcrafters.plot.flag.IntegerFlag;
import com.intellectualcrafters.plot.object.*;
import com.intellectualcrafters.plot.util.*;
import com.plotsquared.listener.PlotListener;
import com.plotsquared.sponge.object.SpongePlayer;
import com.plotsquared.sponge.util.SpongeUtil;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.explosive.Explosive;
import org.spongepowered.api.entity.living.Ambient;
import org.spongepowered.api.entity.living.Hostile;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.animal.Animal;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.vehicle.Boat;
import org.spongepowered.api.entity.vehicle.minecart.Minecart;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.entity.BreedEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.message.MessageEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.event.world.ExplosionEvent.Detonate;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.World;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("Guava")
public class MainListener {
    
    /*
     * TODO:
     *  - Anything marked with a TODO below
     *  - BlockPhysicsEvent
     *  - BlockFormEvent
     *  - BlockFadeEvent
     *  - BlockFromToEvent
     *  - BlockDamageEvent
     *  - Structure (tree etc)
     *  - ChunkPreGenerateEvent
     *  - PlayerIgniteBlockEvent
     *  - PlayerBucketEmptyEvent
     *  - PlayerBucketFillEvent
     *  - VehicleCreateEvent
     *  - HangingPlaceEvent
     *  - HangingBreakEvent
     *  - EntityChangeBlockEvent
     *  - PVP
     *  - block dispense
     *  - PVE
     *  - VehicleDestroy
     *  - Projectile
     *  - enderman harvest
     */

    @Listener
    public void onChat(MessageEvent event) {
        // TODO
        if (event.isMessageCancelled())
            return;

        Player player = SpongeUtil.getCause(event.getCause(), Player.class);
        if (player == null) {
            return;
        }
        String world = player.getWorld().getName();
        if (!PS.get().hasPlotArea(world)) {
            return;
        }
        PlotArea plotworld = PS.get().getPlotAreaByString(world);
        PlotPlayer plr = SpongeUtil.getPlayer(player);
        if (!plotworld.PLOT_CHAT && (plr.getMeta("chat") == null || !(Boolean) plr.getMeta("chat"))) {
            return;
        }
        Location loc = SpongeUtil.getLocation(player);
        Plot plot = loc.getPlot();
        if (plot == null) {
            return;
        }
        Text message = event.getMessage();

        // TODO use display name rather than username
        //  - Getting displayname currently causes NPE, so wait until sponge fixes that

        String sender = player.getName();
        PlotId id = plot.getId();
        String newMessage = StringMan.replaceAll(C.PLOT_CHAT_FORMAT.s(), "%plot_id%", id.x + ";" + id.y, "%sender%", sender);
        //        String forcedMessage = StringMan.replaceAll(C.PLOT_CHAT_FORCED.s(), "%plot_id%", id.x + ";" + id.y, "%sender%", sender);
        for (Entry<String, PlotPlayer> entry : UUIDHandler.getPlayers().entrySet()) {
            PlotPlayer user = entry.getValue();
            String toSend;
            if (plot.equals(user.getLocation().getPlot())) {
                toSend = newMessage;
            } else if (Permissions.hasPermission(user, C.PERMISSION_COMMANDS_CHAT)) {
                ((SpongePlayer) user).getPlayer().ifPresent(player1 -> player1.sendMessage(message));
                continue;
            } else {
                continue;
            }
            String[] split = (toSend + " ").split("%msg%");
            List<Text> components = new ArrayList<>();
            Text prefix = null;
            for (String part : split) {
                if (prefix != null) {
                    components.add(prefix);
                } else {
                    prefix = message;
                }
                components.add(SpongeUtil.getText(part));
            }
            ((SpongePlayer) user).getPlayer().ifPresent(player1 -> player1.sendMessage(Text.join(components)));
        }
        //event.setMessage(null);
    }

    @Listener
    public void onBreedEntity(BreedEntityEvent.Breed event) {
        Location loc = SpongeUtil.getLocation(event.getTargetEntity());
        String world = loc.getWorld();
        PlotArea plotworld = PS.get().getPlotAreaByString(world);
        if (plotworld == null) {
            return;
        }
        Plot plot = loc.getPlot();
        if (plot == null) {
            if (loc.isPlotRoad()) {
                event.setCancelled(true);
            }
            return;
        }
        if (!plotworld.SPAWN_BREEDING) {
            event.setCancelled(true);
        }
    }

    @Listener(order = Order.FIRST,beforeModifications = true)
    public void onSpawnEntity(SpawnEntityEvent event) {
        event.filterEntities(this::filterEntity);
    }

    private boolean filterEntity(Entity entity){
        if(entity instanceof Player){
            return true;
        }

        Location loc = SpongeUtil.getLocation(entity);
        PlotArea area = loc.getPlotArea();
        if(area == null){
            return true;
        }else {
            if(entity instanceof Item){
                Plot plot = area.getPlotAbs(loc);
                if(loc.isPlotRoad()){
                    return !Settings.Enabled_Components.KILL_ROAD_ITEMS;
                }
//                if(plot.getOwners().isEmpty()){
//                    return area.MISC_SPAWN_UNOWNED;
//                }
//                checkEntity(entity,plot,Flags.ENTITY_CAP);
                return plot.getFlag(Flags.ITEM_DROP).or(true);
            }else{
                Plot plot = area.getOwnedPlot(loc);
                if(plot == null){
                    return false;
                }
//                if (entity instanceof Explosive) {
//                    entity.setCreator(plot.owner);
//                }
                if(entity instanceof Living){
                    if(!area.MOB_SPAWNING){
                        return false;
                    }
                    if(entity instanceof Hostile){
                        return checkEntity(plot, Flags.HOSTILE_CAP, Flags.ENTITY_CAP, Flags.MOB_CAP);
                    }else if(entity instanceof Ambient || entity instanceof Animal) {
                        return checkEntity(plot, Flags.ANIMAL_CAP, Flags.ENTITY_CAP, Flags.MOB_CAP);
                    }else {
                        return checkEntity(plot, Flags.ENTITY_CAP, Flags.MOB_CAP);
                    }
                }else if (entity instanceof Minecart || entity instanceof Boat){
                    return checkEntity(plot, Flags.VEHICLE_CAP, Flags.ENTITY_CAP);
                }else {
                    return checkEntity(plot, Flags.ENTITY_CAP);
                }
            }
        }
    }

    private static boolean checkEntity(Plot plot, IntegerFlag... flags) {
        if (Settings.Done.RESTRICT_BUILDING && Flags.DONE.isSet(plot)) {
            return false;
        }
        int[] mobs = null;
        for (IntegerFlag flag : flags) {
            int i;
            switch (flag.getName()) {
                case "entity-cap":
                    i = 0;
                    break;
                case "mob-cap":
                    i = 3;
                    break;
                case "hostile-cap":
                    i = 2;
                    break;
                case "animal-cap":
                    i = 1;
                    break;
                case "vehicle-cap":
                    i = 4;
                    break;
                case "misc-cap":
                    i = 5;
                    break;
                default:
                    i = 0;
            }
            int cap = plot.getFlag(flag, Integer.MAX_VALUE);
            if (cap == Integer.MAX_VALUE) {
                continue;
            }
            if (cap == 0) {
                return false;
            }
            if (mobs == null) {
                mobs = plot.countEntities();
            }
            if (mobs[i] >= cap) {
                plot.setMeta("EntityCount", mobs);
                plot.setMeta("EntityCountTime", System.currentTimeMillis());
                return false;
            }
        }
        if (mobs != null) {
            for (IntegerFlag flag : flags) {
                int i;
                switch (flag.getName()) {
                    case "entity-cap":
                        i = 0;
                        break;
                    case "mob-cap":
                        i = 3;
                        break;
                    case "hostile-cap":
                        i = 2;
                        break;
                    case "animal-cap":
                        i = 1;
                        break;
                    case "vehicle-cap":
                        i = 4;
                        break;
                    case "misc-cap":
                        i = 5;
                        break;
                    default:
                        i = 0;
                }
                mobs[i]++;
            }
            plot.setMeta("EntityCount", mobs);
            plot.setMeta("EntityCountTime", System.currentTimeMillis());
        }
        return true;
    }

    @Listener(order = Order.FIRST,beforeModifications = true)
    public void onBread(BreedEntityEvent.Breed event){
        Entity entity = event.getOffspringEntity();
        Location location = SpongeUtil.getLocation(entity);
        PlotArea area = location.getPlotArea();
        if(area == null){
            return;
        }
        if(!area.SPAWN_BREEDING){
            event.setCancelled(true);
        }
    }

    public void onNotifyNeighborBlock(NotifyNeighborBlockEvent event) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        //        SpongeUtil.printCause("physics", event.getCause());
        //        PlotArea area = plotloc.getPlotArea();
        //        event.filterDirections(new Predicate<Direction>() {
        //            
        //            @Override
        //            public boolean test(Direction dir) {
        //                if (cancelled.get()) {
        //                    return true;
        //                }
        //                org.spongepowered.api.world.Location<World> loc = relatives.get(dir);
        //                com.intellectualcrafters.plot.object.Location plotloc = SpongeUtil.getLocation(loc.getExtent().getPluginName(), loc);
        //                if (area == null) {
        //                    return true;
        //                }
        //                plot = area.get
        //                Plot plot = plotloc.getPlot();
        //                if (plot == null) {
        //                    if (MainUtil.isPlotAreaAbs(plotloc)) {
        //                        cancelled.set(true);
        //                        return false;
        //                    }
        //                    cancelled.set(true);
        //                    return true;
        //                }
        //                org.spongepowered.api.world.Location<World> relative = loc.getRelative(dir);
        //                com.intellectualcrafters.plot.object.Location relLoc = SpongeUtil.getLocation(relative.getExtent().getPluginName(), relative);
        //                if (plot.equals(MainUtil.getPlot(relLoc))) {
        //                    return true;
        //                }
        //                return false;
        //            }
        //        });
    }

    @Listener(order = Order.FIRST,beforeModifications = true)
    public void onInteract(InteractBlockEvent event) {
        Player player = SpongeUtil.getCause(event.getCause(), Player.class);
        if (player == null) {
            cancelInteract(event);
            return;
        }
        BlockSnapshot block = event.getTargetBlock();
        Optional<org.spongepowered.api.world.Location<World>> bloc = block.getLocation();
        if (!bloc.isPresent()) {
            return;
        }
        Location loc = SpongeUtil.getLocation(player.getWorld().getName(), bloc.get());
        PlotArea area = loc.getPlotArea();
        if (area == null) {
            return;
        }
        Plot plot = area.getPlot(loc);
        PlotPlayer pp = SpongeUtil.getPlayer(player);
        if (plot == null) {
            if (!Permissions.hasPermission(pp, C.PERMISSION_ADMIN_INTERACT_ROAD, true)) {
                cancelInteract(event);
                return;
            }
            return;
        }
        if (!plot.hasOwner()) {
            if (Permissions.hasPermission(pp, C.PERMISSION_ADMIN_INTERACT_UNOWNED)) {
                return;
            }
            MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_INTERACT_UNOWNED);
            cancelInteract(event);
            return;
        }
        if (plot.isAdded(pp.getUUID()) || Permissions.hasPermission(pp, C.PERMISSION_ADMIN_INTERACT_OTHER)) {
            return;
        } else {
            com.google.common.base.Optional<HashSet<PlotBlock>> flag = plot.getFlag(Flags.USE);
            org.spongepowered.api.world.Location l = SpongeUtil.getLocation(loc);
            if (flag.isPresent() && flag.get().contains(SpongeUtil.getPlotBlock(l.getBlock()))) {
                return;
            }
            MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_INTERACT_OTHER);
            cancelInteract(event);
            return;
        }
    }

    private void cancelInteract(InteractBlockEvent event){
        event.setCancelled(true);
        if(event instanceof InteractBlockEvent.Secondary){
            ((InteractBlockEvent.Secondary) event).setUseBlockResult(Tristate.FALSE);
            ((InteractBlockEvent.Secondary) event).setUseItemResult(Tristate.FALSE);
        }
    }


    @Listener(order = Order.FIRST,beforeModifications = true)
    public void onExplosion(ExplosionEvent e) {
        if (e instanceof ExplosionEvent.Detonate) {
            ExplosionEvent.Detonate event = (Detonate) e;
            World world = event.getTargetWorld();
            String worldName = world.getName();
            if (!PS.get().hasPlotArea(worldName)) {
                return;
            }
            Optional<Explosive> source = event.getExplosion().getSourceExplosive();
            if (!source.isPresent()) {
                event.setCancelled(true);
                return;
            }
            Explosive tnt = source.get();
            UUID creator = tnt.getCreator().orElse(null);
            Location current = SpongeUtil.getLocation(tnt);
            Plot currentPlot = current.getPlot();
            if (currentPlot == null) {
                if (current.isPlotArea()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (creator != null) {
                if (!currentPlot.isAdded(creator)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!currentPlot.getFlag(Flags.EXPLOSION).or(false)) {
                event.setCancelled(true);
                return;
            }

            event.getAffectedLocations().removeIf(worldLocation -> currentPlot.equals(SpongeUtil.getLocation(worldLocation.getExtent().getName(), worldLocation).getPlot()));
            event.filterEntities(entity -> currentPlot.equals(SpongeUtil.getLocation(entity).getPlot()));
        }
    }

    @Listener(order = Order.FIRST, beforeModifications = true)
    public void onBlockPre(ChangeBlockEvent.Pre event) {
        Player player = SpongeUtil.getCause(event.getCause(), Player.class);
        for(org.spongepowered.api.world.Location<World> location:event.getLocations()){
            Location loc = SpongeUtil.getLocation(location.getExtent().getName(),location);
            if (!loc.isPlotArea()) {
                continue;
            }
            if(player==null){
                event.setCancelled(true);
                return;
            }
            PlotPlayer pp = SpongeUtil.getPlayer(player);
            Plot plot = loc.getPlot();
            if (plot == null) {
                if (!Permissions.hasPermission(pp, C.PERMISSION_ADMIN_DESTROY_ROAD)) {
                    MainUtil.sendMessage(pp, C.PERMISSION_ADMIN_DESTROY_ROAD);
                    event.setCancelled(true);
                    return;
                }
            } else {
                if (!plot.hasOwner()) {
                    if (Permissions.hasPermission(pp, C.PERMISSION_ADMIN_DESTROY_UNOWNED)) {
                        continue;
                    }
                    MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_DESTROY_UNOWNED);
                    event.setCancelled(true);
                    return;
                }
                if (!plot.isAdded(pp.getUUID()) && !Permissions.hasPermission(pp, C.PERMISSION_ADMIN_DESTROY_OTHER)) {
                    MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_DESTROY_OTHER);
                    com.google.common.base.Optional<HashSet<PlotBlock>> destroy = plot.getFlag(Flags.BREAK);
                    BlockState state = location.getBlock();
                    if (!destroy.isPresent() || !destroy.get().contains(SpongeUtil.getPlotBlock(state))) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @Listener(order = Order.FIRST,beforeModifications = true)
    public void onBlockBreak(ChangeBlockEvent.Break event) {
        event.filter(l->{
            Location loc1 = SpongeUtil.getLocation(l.getExtent().getName(), l);
            PlotArea area = loc1.getPlotArea();
            if (area == null) {
                return true;
            }
            Player player = SpongeUtil.getCause(event.getCause(), Player.class);
            if (player == null) {
                return true;
            }
            PlotPlayer pp = SpongeUtil.getPlayer(player);
            Plot plot1 = area.getPlot(loc1);
            if (plot1 == null) {
                if(!Permissions.hasPermission(pp, C.PERMISSION_ADMIN_DESTROY_ROAD, true)){
                    return false;
                }
                return true;
            }
            if (!plot1.hasOwner()) {
                if(!Permissions.hasPermission(pp, C.PERMISSION_ADMIN_DESTROY_UNOWNED, true)){
                    return false;
                }
                return true ;
            }
            if (!plot1.isAdded(pp.getUUID()) && !Permissions.hasPermission(pp, C.PERMISSION_ADMIN_DESTROY_OTHER, true)) {
                com.google.common.base.Optional<HashSet<PlotBlock>> destroy = plot1.getFlag(Flags.BREAK);
                BlockState state = l.getBlock();
                if (destroy.isPresent() && destroy.get().contains(SpongeUtil.getPlotBlock(state))) {
                    return true ;
                }
                MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_DESTROY_OTHER);
                return false;
            }
            return true;
        });
    }
    @Listener(order = Order.FIRST,beforeModifications = true)
    public void onBlockPlace(ChangeBlockEvent.Place event) {
        event.filter(l->{
            Location loc1 = SpongeUtil.getLocation(l.getExtent().getName(), l);
            PlotArea area = loc1.getPlotArea();
            if (area == null) {
                return true ;
            }
            Player player = SpongeUtil.getCause(event.getCause(), Player.class);
            if (player == null) {
                return true;
            }
            PlotPlayer pp = SpongeUtil.getPlayer(player);
            Plot plot1 = area.getPlot(loc1);
            if (plot1 == null) {
                if(!Permissions.hasPermission(pp, C.PERMISSION_ADMIN_BUILD_ROAD, true)){
                    return false;
                }
                return true;
            }
            if (!plot1.hasOwner()) {
                if(!Permissions.hasPermission(pp, C.PERMISSION_ADMIN_BUILD_UNOWNED, true)){
                    return false;
                }
                return true ;
            }
            if (!plot1.isAdded(pp.getUUID()) && !Permissions.hasPermission(pp, C.PERMISSION_ADMIN_BUILD_OTHER, true)) {
                com.google.common.base.Optional<HashSet<PlotBlock>> destroy = plot1.getFlag(Flags.PLACE);
                BlockState state = l.getBlock();
                if (destroy.isPresent() && destroy.get().contains(SpongeUtil.getPlotBlock(state))) {
                    return true ;
                }
                MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_BUILD_OTHER);
                return false;
            }
            return true;
        });
    }

    @Listener
    public void onJoin(ClientConnectionEvent.Join event) {
        Player player = event.getTargetEntity();
        SpongeUtil.getPlayer(player).unregister();
        PlotPlayer pp = SpongeUtil.getPlayer(player);
        // Now
        String name = pp.getName();
        StringWrapper sw = new StringWrapper(name);
        UUID uuid = pp.getUUID();
        UUIDHandler.add(sw, uuid);

        Location loc = pp.getLocation();
        PlotArea area = loc.getPlotArea();
        Plot plot;
        if (area != null) {
            plot = area.getPlot(loc);
            if (plot != null) {
                PlotListener.plotEntry(pp, plot);
            }
        } else {
            plot = null;
        }
        // Delayed

        // Async
        TaskManager.runTaskLaterAsync(() -> EventUtil.manager.doJoinTask(pp), 20);
    }

    @Listener
    public void onQuit(ClientConnectionEvent.Disconnect event) {
        Player player = event.getTargetEntity();
        PlotPlayer pp = SpongeUtil.getPlayer(player);
        pp.unregister();
    }

    @Listener
    public void onMove(MoveEntityEvent event) {
        if (!(event.getTargetEntity() instanceof Player)) {
            return;
        }
        org.spongepowered.api.world.Location<World> from = event.getFromTransform().getLocation();
        org.spongepowered.api.world.Location<World> to = event.getToTransform().getLocation();
        int x2;
        if (MathMan.roundInt(from.getX()) != (x2 = MathMan.roundInt(to.getX()))) {
            Player player = (Player) event.getTargetEntity();
            PlotPlayer pp = SpongeUtil.getPlayer(player);
            // Cancel teleport
            TaskManager.TELEPORT_QUEUE.remove(pp.getName());
            // Set last location
            Location loc = SpongeUtil.getLocation(to);
            pp.setMeta("location", loc);
            PlotArea area = loc.getPlotArea();
            if (area == null) {
                pp.deleteMeta("lastplot");
                return;
            }
            Plot now = area.getPlotAbs(loc);
            Plot lastPlot = pp.getMeta("lastplot");
            if (now == null) {
                if (lastPlot != null && !PlotListener.plotExit(pp, lastPlot)) {
                    MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_EXIT_DENIED);
                    if (lastPlot.equals(SpongeUtil.getLocation(from).getPlot())) {
                        player.setLocation(from);
                    } else {
                        player.setLocation(player.getWorld().getSpawnLocation());
                    }
                    event.setCancelled(true);
                    return;
                }
            } else if (now.equals(lastPlot)) {
                ForceFieldListener.handleForcefield(player, pp, now);
                return;
            } else if (!PlotListener.plotEntry(pp, now)) {
                MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_ENTRY_DENIED);
                player.setLocation(from);
                event.setCancelled(true);
                return;
            }
            Integer border = area.getBorder();
            if (x2 > border) {
                to.sub(x2 - border + 4, 0, 0);
                player.setLocation(to);
                MainUtil.sendMessage(pp, C.BORDER);
                return;
            } else if (x2 < -border) {
                to.add(border - x2 + 4, 0, 0);
                player.setLocation(to);
                MainUtil.sendMessage(pp, C.BORDER);
                return;
            }
            return;
        }
        int z2;
        if (MathMan.roundInt(from.getZ()) != (z2 = MathMan.roundInt(to.getZ()))) {
            Player player = (Player) event.getTargetEntity();
            PlotPlayer pp = SpongeUtil.getPlayer(player);
            // Cancel teleport
            TaskManager.TELEPORT_QUEUE.remove(pp.getName());
            // Set last location
            Location loc = SpongeUtil.getLocation(to);
            pp.setMeta("location", loc);
            PlotArea area = loc.getPlotArea();
            if (area == null) {
                pp.deleteMeta("lastplot");
                return;
            }
            Plot now = area.getPlotAbs(loc);
            Plot lastPlot = pp.getMeta("lastplot");
            if (now == null) {
                if (lastPlot != null && !PlotListener.plotExit(pp, lastPlot)) {
                    MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_EXIT_DENIED);
                    if (lastPlot.equals(SpongeUtil.getLocation(from).getPlot())) {
                        player.setLocation(from);
                    } else {
                        player.setLocation(player.getWorld().getSpawnLocation());
                    }
                    event.setCancelled(true);
                    return;
                }
            } else if (now.equals(lastPlot)) {
                ForceFieldListener.handleForcefield(player, pp, now);
                return;
            } else if (!PlotListener.plotEntry(pp, now)) {
                MainUtil.sendMessage(pp, C.NO_PERMISSION_EVENT, C.PERMISSION_ADMIN_ENTRY_DENIED);
                player.setLocation(from);
                event.setCancelled(true);
                return;
            }
            Integer border = area.getBorder();
            if (z2 > border) {
                to.add(0, 0, z2 - border - 4);
                player.setLocation(to);
                MainUtil.sendMessage(pp, C.BORDER);
            } else if (z2 < -border) {
                to.add(0, 0, border - z2 + 4);
                player.setLocation(to);
                MainUtil.sendMessage(pp, C.BORDER);
            }
        }
    }
}
