package com.chedidandrew.emeraldstandard.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Checks the actual packaged fence geometry, including all multipart rotations. No world required. */
public final class ConstructionFenceModelSelfTest {
    private static final String ROOT = "/assets/the_emerald_standard/";
    private static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");
    private static final double EPS = 0.000001;
    private record Part(double[] from, double[] to, Map<String, String> faces) {
        Part transform(int turns, double x, double z) {
            double[] a = from.clone(), b = to.clone();
            var f = new HashMap<>(faces);
            for (int i = 0; i < turns; i++) {
                double lo = a[0], hi = b[0];
                a[0] = 16 - b[2]; b[0] = 16 - a[2]; a[2] = lo; b[2] = hi;
                var rotated = new HashMap<String, String>();
                f.forEach((dir, tex) -> rotated.put(DIRECTIONS.contains(dir)
                        ? DIRECTIONS.get((DIRECTIONS.indexOf(dir) + 1) % 4) : dir, tex));
                f = rotated;
            }
            a[0] += x; b[0] += x; a[2] += z; b[2] += z;
            return new Part(a, b, f);
        }
        String signature() { return Arrays.toString(from) + Arrays.toString(to) + new java.util.TreeMap<>(faces); }
    }
    private static JsonObject json(String path) {
        try (var in = ConstructionFenceModelSelfTest.class.getResourceAsStream(ROOT + path)) {
            if (in == null) throw new IllegalStateException("Missing fence resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private static List<Part> parts(JsonObject json) {
        var out = new ArrayList<Part>();
        for (var element : json.getAsJsonArray("elements")) {
            var e = element.getAsJsonObject();
            require(!e.has("rotation"), "Unexpected element rotation: extend geometry check");
            double[] a = new double[3], b = new double[3];
            for (int i = 0; i < 3; i++) {
                a[i] = e.getAsJsonArray("from").get(i).getAsDouble();
                b[i] = e.getAsJsonArray("to").get(i).getAsDouble();
                require(a[i] >= 0 && b[i] <= 16 && b[i] > a[i], "Invalid fence cuboid");
            }
            var faces = new HashMap<String, String>();
            e.getAsJsonObject("faces").entrySet().forEach(entry ->
                    faces.put(entry.getKey(), entry.getValue().getAsJsonObject().get("texture").getAsString()));
            out.add(new Part(a, b, faces));
        }
        return out;
    }
    private static double overlap(Part a, Part b, int axis) {
        return Math.min(a.to[axis], b.to[axis]) - Math.max(a.from[axis], b.from[axis]);
    }
    private static void clean(String name, List<Part> parts) {
        for (int i = 0; i < parts.size(); i++) for (int j = i + 1; j < parts.size(); j++) {
            Part a = parts.get(i), b = parts.get(j);
            for (String dir : a.faces.keySet()) {
                if (!b.faces.containsKey(dir)) continue;
                int axis = switch (dir) { case "east", "west" -> 0; case "up", "down" -> 1; default -> 2; };
                boolean positive = List.of("east", "up", "south").contains(dir);
                double pa = positive ? a.to[axis] : a.from[axis], pb = positive ? b.to[axis] : b.from[axis];
                require(Math.abs(pa - pb) > EPS || overlap(a, b, (axis + 1) % 3) <= EPS
                        || overlap(a, b, (axis + 2) % 3) <= EPS,
                        name + ": overlapping coplanar " + dir + " faces in elements " + i + "/" + j);
            }
            require(overlap(a, b, 0) <= EPS || overlap(a, b, 1) <= EPS || overlap(a, b, 2) <= EPS,
                    name + ": overlapping solid cuboids " + i + "/" + j);
        }
    }
    private static List<Part> assembly(int mask, JsonObject state) {
        var out = new ArrayList<Part>();
        for (var entry : state.getAsJsonArray("multipart")) {
            var e = entry.getAsJsonObject();
            if (e.has("when")) {
                var when = e.getAsJsonObject("when");
                require(when.size() == 1, "Unexpected fence condition");
                String dir = when.keySet().iterator().next();
                int bit = DIRECTIONS.indexOf(dir);
                require(bit >= 0 && when.get(dir).getAsBoolean(), "Unexpected fence direction");
                if ((mask & (1 << bit)) == 0) continue;
            }
            var apply = e.getAsJsonObject("apply");
            String model = apply.get("model").getAsString().split(":")[1];
            int rotation = apply.has("y") ? apply.get("y").getAsInt() : 0;
            require(rotation >= 0 && rotation < 360 && rotation % 90 == 0, "Unexpected fence rotation");
            for (Part p : parts(json("models/" + model + ".json"))) out.add(p.transform(rotation / 90, 0, 0));
        }
        return out;
    }
    public static void verify() {
        var state = json("blockstates/construction_fence.json");
        require(state.getAsJsonArray("multipart").size() == 5, "Fence connections missing");
        var post = parts(json("models/block/construction_fence_post.json"));
        clean("Post", post);
        var side = parts(json("models/block/construction_fence_side.json"));
        clean("Rail", side);
        for (int mask = 0; mask < 16; mask++) clean("Connections " + mask, assembly(mask, state));
        // Connected neighboring blocks, including corners and cross-junctions.
        for (int dir = 0; dir < 4; dir++) for (int mask = 0; mask < 16; mask++) {
            var joined = new ArrayList<>(assembly(mask | (1 << dir), state));
            double x = dir == 1 ? 16 : dir == 3 ? -16 : 0;
            double z = dir == 2 ? 16 : dir == 0 ? -16 : 0;
            for (Part p : assembly(15, state)) joined.add(p.transform(0, x, z));
            clean("Neighbor " + dir + "/" + mask, joined);
        }
        var itemJson = json("models/item/construction_fence.json");
        var item = parts(itemJson);
        clean("Item", item);
        require(item.stream().map(Part::signature).sorted().toList().equals(
                assembly(5, state).stream().map(Part::signature).sorted().toList()), "Item differs from N/S block");
        require(item.stream().mapToInt(p -> p.faces.size()).sum() <= 186, "Fence quad budget increased");
        require(itemJson.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("scale")
                .get(0).getAsDouble() == 0.8, "GUI size changed");
        // Preserve the stepped stripe pattern; no holes, thin overlays or hidden backing.
        for (int base : new int[] {4, 11}) for (int row = 0; row < 3; row++) {
            for (double z = 0.25; z < 6.5; z += 0.5) {
                int count = 0;
                String expected = Math.floorMod((int) Math.floor(z) - row, 4) < 2 ? "#black" : "#yellow";
                for (Part p : side) if (p.from[1] < base + row + .5 && p.to[1] > base + row + .5
                        && p.from[2] < z && p.to[2] > z) {
                    count++;
                    require(p.from[0] == 7.5 && p.to[0] == 8.5, "Rail thickness changed");
                    require(expected.equals(p.faces.get("east")) && expected.equals(p.faces.get("west")),
                            "Stripe color changed at " + base + "/" + row + "/" + z);
                }
                require(count == 1, "Rail gap or overlapping stripe at " + z);
            }
        }
        Part stem = post.stream().filter(p -> p.faces.containsValue("#wood")).findFirst().orElseThrow();
        require(stem.from[1] == 2 && stem.to[1] == 14, "Stem penetrates cap or foot");
        require(!stem.faces.containsKey("up") && !stem.faces.containsKey("down"), "Hidden stem ends rendered");
        System.out.println("Construction fence models passed: 16 connections, 64 neighbors, item, stripes and quad budget");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public static void main(String[] args) { verify(); }
}
