package com.chedidandrew.emeraldstandard.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.slf4j.LoggerFactory;

/** Snapshot recipe displays once when the reader opens, never query registries each frame. */
final class HandbookRecipes {
    record Recipe(List<List<ItemStack>> ingredients, List<ItemStack> results,
                  boolean shapeless, boolean serverRecipe) {
        int variants() {
            return ingredients.stream().mapToInt(List::size).max().orElse(1);
        }
        ItemStack[] frame(long frame) {
            ItemStack[] grid = new ItemStack[9];
            java.util.Arrays.fill(grid, ItemStack.EMPTY);
            for (int i = 0; i < ingredients.size(); i++) {
                var choices = ingredients.get(i);
                if (!choices.isEmpty()) grid[shapeless
                        ? RecipeAnimation.shapelessSlot(i, ingredients.size(), frame) : i]
                        = choices.get((int) Math.floorMod(frame, choices.size()));
            }
            return grid;
        }
        ItemStack result(long frame) {
            return results.isEmpty() ? ItemStack.EMPTY : results.get((int) Math.floorMod(frame, results.size()));
        }
    }

    private HandbookRecipes() { }

    static List<Recipe> load(String section) {
        String id = switch (section) {
            case "recipe_desk" -> "exchange_desk";
            case "recipe_book" -> "handbook";
            case "recipe_fence" -> "construction_fence";
            case "recipe_newspaper" -> "newspaper";
            default -> null;
        };
        if (id == null) return List.of();
        Minecraft game = Minecraft.getInstance();
        var target = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("the_emerald_standard", id));
        List<Recipe> recipes = new ArrayList<>();
        // Minecraft syncs unlocked recipe displays, not its entire server recipe manager.
        // Prefer those actual displays (including datapacks) whenever the output is known.
        if (game.level != null && game.player != null) {
            var context = SlotDisplayContext.fromLevel(game.level);
            Set<Object> seen = new HashSet<>();
            for (var collection : game.player.getRecipeBook().getCollections()) {
                for (var entry : collection.getRecipes()) {
                    if (!seen.add(entry.id())) continue;
                    var display = entry.display();
                    var results = display.result().resolveForStacks(context);
                    if (results.stream().noneMatch(stack -> stack.is(target))) continue;
                    if (display instanceof ShapedCraftingRecipeDisplay shaped
                            && shaped.width() <= 3 && shaped.height() <= 3) {
                        List<List<ItemStack>> grid = new ArrayList<>();
                        for (int slot = 0; slot < 9; slot++) {
                            int x = slot % 3, y = slot / 3;
                            grid.add(x < shaped.width() && y < shaped.height()
                                    ? shaped.ingredients().get(y * shaped.width() + x).resolveForStacks(context)
                                    : List.of());
                        }
                        recipes.add(new Recipe(List.copyOf(grid), results, false, true));
                    } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless
                            && !shapeless.ingredients().isEmpty() && shapeless.ingredients().size() <= 9) {
                        recipes.add(new Recipe(shapeless.ingredients().stream()
                                .map(slot -> slot.resolveForStacks(context)).toList(), results, true, true));
                    }
                }
            }
        }
        if (!recipes.isEmpty()) return List.copyOf(recipes);
        // Teach the bundled recipe even before it is unlocked; clearly label this fallback.
        try {
            JsonObject json = json("data/the_emerald_standard/recipe/" + id + ".json");
            boolean shapeless = json.get("type").getAsString().equals("minecraft:crafting_shapeless");
            List<List<ItemStack>> ingredients = new ArrayList<>();
            if (shapeless) {
                for (var value : json.getAsJsonArray("ingredients"))
                    ingredients.add(ingredient(value.getAsString(), game.level != null, new HashSet<>()));
            } else {
                var pattern = json.getAsJsonArray("pattern");
                var keys = json.getAsJsonObject("key");
                for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                    String row = y < pattern.size() ? pattern.get(y).getAsString() : "";
                    String key = x < row.length() ? row.substring(x, x + 1) : " ";
                    ingredients.add(key.equals(" ") ? List.of()
                            : ingredient(keys.get(key).getAsString(), game.level != null, new HashSet<>()));
                }
            }
            var result = json.getAsJsonObject("result");
            ItemStack output = previewStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(result.get("id").getAsString())),
                    result.has("count") ? result.get("count").getAsInt() : 1);
            return List.of(new Recipe(List.copyOf(ingredients), List.of(output), shapeless, false));
        } catch (java.io.IOException | RuntimeException exception) {
            LoggerFactory.getLogger(HandbookRecipes.class).warn("Could not load handbook recipe {}", id, exception);
            return List.of();
        }
    }

    private static List<ItemStack> ingredient(String value, boolean inWorld, Set<String> visited)
            throws java.io.IOException {
        if (!value.startsWith("#")) {
            ItemStack item = previewStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(value)), 1);
            return item.isEmpty() ? List.of() : List.of(item);
        }
        Identifier id = Identifier.parse(value.substring(1));
        List<ItemStack> choices = new ArrayList<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id)))
            choices.add(previewStack(holder.value(), 1));
        // Title-screen reader has no synchronized tags. Read vanilla defaults only there.
        if (choices.isEmpty() && !inWorld && visited.add(value)) {
            for (var entry : json("data/" + id.getNamespace() + "/tags/item/" + id.getPath() + ".json")
                    .getAsJsonArray("values")) {
                String nested = entry.isJsonObject() ? entry.getAsJsonObject().get("id").getAsString() : entry.getAsString();
                choices.addAll(ingredient(nested, false, visited));
            }
        }
        return List.copyOf(choices);
    }

    private static JsonObject json(String path) throws java.io.IOException {
        var stream = HandbookRecipes.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) throw new java.io.IOException("Missing recipe resource: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static ItemStack previewStack(net.minecraft.world.item.Item item, int count) {
        if (item.builtInRegistryHolder().areComponentsBound()) return new ItemStack(item, count);
        // 26.2 binds normal item components only after joining a world. A title-screen
        // preview needs its own display-only holder; never bind or alter global registries.
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
                .set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                        net.minecraft.network.chat.Component.translatable(item.getDescriptionId()))
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build();
        return new ItemStack(net.minecraft.core.Holder.direct(item, components), count);
    }
}
