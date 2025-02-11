package io.github.flemmli97.simplequests.quest;

import com.google.gson.JsonObject;
import io.github.flemmli97.simplequests.SimpleQuests;
import io.github.flemmli97.simplequests.config.ConfigHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public class QuestEntryImpls {

    public static class IngredientEntry implements QuestEntry {

        public static final ResourceLocation id = new ResourceLocation(SimpleQuests.MODID, "ingredient");

        public final Ingredient ingredient;
        public final int amount;
        public final MutableComponent description;

        public IngredientEntry(Ingredient ingredient, int amount, String description) {
            this.ingredient = ingredient;
            this.amount = amount;
            this.description = description.isEmpty() ? null : Component.literal(description);
        }

        @Override
        public boolean submit(ServerPlayer player) {
            List<ItemStack> matching = new ArrayList<>();
            int i = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (this.ingredient.test(stack)) {
                    if (stack.isDamageableItem()) {
                        if (stack.getDamageValue() != 0) {
                            continue;
                        }
                    }
                    //Ignore "special" items
                    if (!this.isJustRenamedItem(stack)) {
                        continue;
                    }
                    matching.add(stack);
                    i += stack.getCount();
                }
            }
            if (i < this.amount)
                return false;
            i = this.amount;
            for (ItemStack stack : matching) {
                if (i > stack.getCount()) {
                    int count = stack.getCount();
                    stack.setCount(0);
                    i -= count;
                } else {
                    stack.shrink(i);
                    break;
                }
            }
            return true;
        }

        private boolean isJustRenamedItem(ItemStack stack) {
            if (!stack.hasTag())
                return true;
            if (stack.getTag().getAllKeys()
                    .stream().allMatch(s -> s.equals("Damage") || s.equals("RepairCost") || s.equals("display"))) {
                CompoundTag tag = stack.getTag().getCompound("display");
                return tag.contains("Name") && tag.size() == 1;
            }
            return true;
        }

        @Override
        public JsonObject serialize() {
            JsonObject obj = new JsonObject();
            obj.add("ingredient", this.ingredient.toJson());
            obj.addProperty("amount", this.amount);
            return obj;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public MutableComponent translation(MinecraftServer server) {
            if (this.description != null)
                return this.description;
            if (this.ingredient.getItems().length == 0)
                return Component.literal(ConfigHandler.lang.get(this.getId().toString() + ".empty"));
            if (this.ingredient.getItems().length == 1) {
                return Component.translatable(ConfigHandler.lang.get(this.getId().toString() + ".single"), Component.translatable(this.ingredient.getItems()[0].getItem().getDescriptionId()).withStyle(ChatFormatting.AQUA), this.amount);
            }
            MutableComponent items = null;
            for (ItemStack stack : this.ingredient.getItems()) {
                if (items == null)
                    items = Component.literal("[").append(Component.translatable(stack.getItem().getDescriptionId()));
                else
                    items.append(Component.literal(", ")).append(Component.translatable(stack.getItem().getDescriptionId()));
            }
            items.append("]");
            return Component.translatable(ConfigHandler.lang.get(this.getId().toString() + ".multi"), items.withStyle(ChatFormatting.AQUA), this.amount);
        }


        public static IngredientEntry fromJson(JsonObject obj) {
            return new IngredientEntry(Ingredient.fromJson(GsonHelper.getAsJsonObject(obj, "ingredient")), obj.get("amount").getAsInt(),
                    GsonHelper.getAsString(obj, "description", ""));
        }
    }

    public record KillEntry(ResourceLocation entity, int amount) implements QuestEntry {

        public static final ResourceLocation id = new ResourceLocation(SimpleQuests.MODID, "entity");

        @Override
        public boolean submit(ServerPlayer player) {
            return false;
        }

        @Override
        public JsonObject serialize() {
            JsonObject obj = new JsonObject();
            obj.addProperty("entity", this.entity.toString());
            obj.addProperty("amount", this.amount);
            return obj;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public MutableComponent translation(MinecraftServer server) {
            return Component.translatable(ConfigHandler.lang.get(this.getId().toString()), Component.translatable(Util.makeDescriptionId("entity", this.entity)).withStyle(ChatFormatting.AQUA), this.amount);
        }

        public static KillEntry fromJson(JsonObject obj) {
            return new KillEntry(new ResourceLocation(GsonHelper.getAsString(obj, "entity")), GsonHelper.getAsInt(obj, "amount", 1));
        }
    }

    public record XPEntry(int amount) implements QuestEntry {

        public static final ResourceLocation id = new ResourceLocation(SimpleQuests.MODID, "xp");

        @Override
        public boolean submit(ServerPlayer player) {
            if (player.experienceLevel >= this.amount) {
                player.giveExperienceLevels(-this.amount);
                return true;
            }
            return false;
        }

        @Override
        public JsonObject serialize() {
            JsonObject obj = new JsonObject();
            obj.addProperty("amount", this.amount);
            return obj;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public MutableComponent translation(MinecraftServer server) {
            return Component.literal(String.format(ConfigHandler.lang.get(this.getId().toString()), this.amount));
        }

        public static XPEntry fromJson(JsonObject obj) {
            return new XPEntry(GsonHelper.getAsInt(obj, "amount", 1));
        }
    }

    public record AdvancementEntry(ResourceLocation advancement) implements QuestEntry {

        public static final ResourceLocation id = new ResourceLocation(SimpleQuests.MODID, "advancement");

        @Override
        public boolean submit(ServerPlayer player) {
            Advancement adv = player.getServer().getAdvancements().getAdvancement(this.advancement);
            return adv != null && (player.getAdvancements().getOrStartProgress(adv).isDone());
        }

        @Override
        public JsonObject serialize() {
            JsonObject obj = new JsonObject();
            obj.addProperty("advancement", this.advancement.toString());
            return obj;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }


        @Override
        public MutableComponent translation(MinecraftServer server) {
            Advancement advancement = server.getAdvancements().getAdvancement(this.advancement());
            Component adv;
            if (advancement == null)
                adv = Component.literal(String.format(ConfigHandler.lang.get("simplequests.missing.advancement"), this.advancement()));
            else
                adv = advancement.getChatComponent();
            return Component.translatable(ConfigHandler.lang.get(this.getId().toString()), adv);
        }

        public static AdvancementEntry fromJson(JsonObject obj) {
            return new AdvancementEntry(new ResourceLocation(GsonHelper.getAsString(obj, "advancement")));
        }
    }
}
