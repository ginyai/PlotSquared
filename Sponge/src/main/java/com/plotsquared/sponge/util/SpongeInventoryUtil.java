package com.plotsquared.sponge.util;

import com.intellectualcrafters.plot.object.PlotInventory;
import com.intellectualcrafters.plot.object.PlotItemStack;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.intellectualcrafters.plot.util.InventoryUtil;
import com.plotsquared.sponge.SpongeMain;
import com.plotsquared.sponge.object.SpongePlayer;
import net.minecraft.item.Item;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.*;
import org.spongepowered.api.item.inventory.property.*;
import org.spongepowered.api.item.inventory.query.QueryOperationTypes;
import org.spongepowered.api.item.inventory.type.CarriedInventory;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;


public class SpongeInventoryUtil extends InventoryUtil {

    public SpongeInventoryUtil() {
    }

    @Override
    public void open(final PlotInventory inv) {

        final SpongePlayer sp = (SpongePlayer) inv.player;
        Optional<Player> optionalPlayer = sp.getPlayer();
        if(!optionalPlayer.isPresent()) {
            return;
        }
        Player player = optionalPlayer.get();
        Text title = Text.of(inv.getTitle());
        Inventory inventory = Inventory.builder()
                .property(InventoryTitle.PROPERTY_NAME, InventoryTitle.of(title))
                .property(InventoryDimension.PROPERTY_NAME, InventoryDimension.of(9, inv.size))
                .property("plotsquared", StringProperty.of(inv.getTitle()))
                .build(SpongeMain.THIS);
        //name(SpongeUtil.getTranslation(inv.getTitle())).size(inv.size).build();
        final PlotItemStack[] items = inv.getItems();
        for (int i = 0; i < (inv.size * 9); i++) {
            final PlotItemStack item = items[i];
            if (item != null) {
                inventory.query(QueryOperationTypes.INVENTORY_PROPERTY.of(SlotIndex.of(i))).set(getItem(item));
            }
        }
        inv.player.setMeta("inventory", inv);
        player.openInventory(inventory);
    }

    public ItemStack getItem(final PlotItemStack item) {
        net.minecraft.item.ItemStack itemStack = new net.minecraft.item.ItemStack(Item.getItemById(item.id), item.amount, item.data);
        itemStack.setStackDisplayName(item.name);
        ItemStack spongeItem = ItemStackUtil.fromNative(itemStack);
        spongeItem.tryOffer(Keys.ITEM_LORE, Arrays.stream(item.lore).map(TextSerializers.LEGACY_FORMATTING_CODE::deserialize).collect(Collectors.toList()));
        return spongeItem;
    }

    @Override
    public void close(final PlotInventory inv) {
        if (!inv.isOpen()) {
            return;
        }
        inv.player.deleteMeta("inventory");
        final SpongePlayer sp = (SpongePlayer) inv.player;
        sp.getPlayer().ifPresent(Player::closeInventory);
    }

    @Override
    public void setItem(final PlotInventory inv, final int index, final PlotItemStack item) {
        if (!inv.isOpen()) {
            return;
        }
        final SpongePlayer sp = (SpongePlayer) inv.player;
        Optional<Player> optionalPlayer = sp.getPlayer();
        if(!optionalPlayer.isPresent()) {
            return;
        }
        Player player = optionalPlayer.get();
        if(!inv.isOpen()) {
            return;
        }
        Optional<Container> optionalContainer = player.getOpenInventory();
        if(!optionalContainer.isPresent()) {
            return;
        }
        Container container = optionalContainer.get();
        container.query(QueryOperationTypes.INVENTORY_PROPERTY.of(SlotIndex.of(index))).set(getItem(item));
    }

    public PlotItemStack getItem(final ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemType type = item.getType();
        String id = type.getId();
        int amount = item.getQuantity();
        String name = item.get(Keys.DISPLAY_NAME).map(TextSerializers.LEGACY_FORMATTING_CODE::serialize).orElse(type.getName());
        String[] lore = item.get(Keys.ITEM_LORE).orElse(Collections.emptyList()).stream().map(TextSerializers.LEGACY_FORMATTING_CODE::serialize).toArray(String[]::new);
        return new PlotItemStack(id, amount, name, lore);
    }

    @Override
    public PlotItemStack[] getItems(final PlotPlayer plotPlayer) {
        final SpongePlayer sp = (SpongePlayer) plotPlayer;
        Optional<Player> optionalPlayer = sp.getPlayer();
        if(!optionalPlayer.isPresent()) {
            return new PlotItemStack[0];
        }
        Player player = optionalPlayer.get();
        Inventory inventory = player.getInventory();
        PlotItemStack[] items = new PlotItemStack[36];
        for (int i = 0; i < 36; i++) {
            items[i] = getItem(inventory.query(QueryOperationTypes.INVENTORY_PROPERTY.of(SlotIndex.of(i))).peek().orElse(ItemStack.empty()));
        }
        return items;
    }

    @Override
    public boolean isOpen(final PlotInventory inv) {
        if (!inv.isOpen()) {
            return false;
        }
        final SpongePlayer sp = (SpongePlayer) inv.player;
        Optional<Player> optionalPlayer = sp.getPlayer();
        if(!optionalPlayer.isPresent()) {
            return false;
        }
        Player player = optionalPlayer.get();
        if (player.isViewingInventory()) {
            final CarriedInventory<? extends Carrier> inventory = player.getInventory();
            return inventory.getProperty(StringProperty.class, "plotsquared").map(stringProperty -> stringProperty.getValue().equals(inv.getTitle())).orElse(false);
        }
        return false;
    }

}
