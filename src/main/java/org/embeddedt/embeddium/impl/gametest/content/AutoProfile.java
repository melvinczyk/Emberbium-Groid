package org.embeddedt.embeddium.impl.gametest.content;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only unattended benchmark: creates a superflat world full of block entities, spins the camera,
 * records FPS and runs a spark render-thread profile, then exits. Enabled with -Dembeddium.autoProfile=true.
 */
public class AutoProfile {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Groidberbium/AutoProfile");
    public static final boolean ENABLED = Boolean.getBoolean("embeddium.autoProfile");
    private static final int WARMUP_TICKS = 300;
    private static final int MEASURE_TICKS = 20 * 65;
    // "blockentities" (default): superflat packed with block entities. "terrain": normal worldgen, flying forward
    // "rebuild": normal worldgen, standing still, times repeated full rebuilds of every chunk mesh
    private static final boolean REBUILD = "rebuild".equals(System.getProperty("embeddium.autoProfile.scenario"));
    // "distance": render distance 24, fixed spot, A/B of the render distance ellipsoid on FPS and visible sections
    private static final boolean DISTANCE = "distance".equals(System.getProperty("embeddium.autoProfile.scenario"));
    // "pack": normal worldgen, standing at spawn turning slowly, for profiling a full modpack whose worldgen can't keep
    // up with flying
    private static final boolean PACK = "pack".equals(System.getProperty("embeddium.autoProfile.scenario"));
    private static final boolean TERRAIN = REBUILD || DISTANCE || PACK || "terrain".equals(System.getProperty("embeddium.autoProfile.scenario"));
    // "entities": superflat at night with ~625 idle mobs (vanilla and pack mods) around a slowly turning camera
    private static final boolean ENTITIES = "entities".equals(System.getProperty("embeddium.autoProfile.scenario"));
    private static long rebuildStart;
    private static int rebuildsDone;
    private static final List<Long> rebuildMillis = new ArrayList<>();

    // Comma-separated features to A/B within one run, alternating every AB_WINDOW ticks so both variants share the
    // JVM, warm-up and thermal state: "esfog" (Ecliptic Seasons fog gate), "ifhud" (ImmediatelyFast HUD batching),
    // "esedges" (Ecliptic Seasons smooth snowy edges, rebuild scenario), "esenh" (Ecliptic Seasons periodic full re-mesh), "essmart" (Groid ES fork: skip unchanged re-mesh), "prain" (Particle Rain direct vertex writes), "minimap" (Remnant Minimaps rescan gate and label batching),
    // "fieldguide" (Field Guide entry id cache), "voicemute" (Simple Voice Chat idles the microphone while muted)
    private static final String[] AB = System.getProperty("embeddium.autoProfile.ab", "").isEmpty() ? new String[0] : System.getProperty("embeddium.autoProfile.ab").split(",");
    private static final int AB_WINDOW = 200;
    private static final List<Integer> abFpsOn = new ArrayList<>(), abFpsOff = new ArrayList<>();

    private static void setAbFeatures(boolean on) {
        for (String feature : AB) {
            switch (feature) {
                case "esfog" -> org.embeddedt.embeddium.compat.eclipticseasons.EclipticSeasonsFogGate.enabled = on;
                case "prain" -> org.embeddedt.embeddium.compat.particlerain.ParticleRainQuads.enabled = on;
                case "minimap" -> org.embeddedt.embeddium.compat.remnantminimaps.RemnantScanGate.enabled = on;
                case "fieldguide" -> org.embeddedt.embeddium.compat.fieldguide.FieldGuideIdCache.enabled = on;
                case "voicemute" -> org.embeddedt.embeddium.compat.voicechat.VoiceChatMuteGate.enabled = on;
                case "esedges" -> setEclipticSeasonsOption("Debug", "smoothSnowyEdges", on);
                case "esenh" -> setEclipticSeasonsOption("Renderer", "enhancementChunkRenderUpdate", on);
                case "essmart" -> setEclipticSeasonsOption("Renderer", "smartChunkRenderUpdate", on);
                case "ifhud" -> {
                    try {
                        Object config = Class.forName("net.raphimc.immediatelyfast.ImmediatelyFast").getField("runtimeConfig").get(null);
                        config.getClass().getField("hud_batching").setBoolean(config, on);
                    } catch (ReflectiveOperationException e) {
                        LOGGER.warn("AUTOPROFILE cannot toggle ImmediatelyFast HUD batching", e);
                    }
                }
                default -> LOGGER.warn("AUTOPROFILE unknown A/B feature {}", feature);
            }
        }
    }

    private static void setEclipticSeasonsOption(String category, String field, boolean value) {
        try {
            Object option = Class.forName("com.teamtea.eclipticseasons.config.ClientConfig$" + category).getField(field).get(null);
            ((net.minecraftforge.common.ForgeConfigSpec.BooleanValue) option).set(value);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("AUTOPROFILE cannot set Ecliptic Seasons option {}", field, e);
        }
    }

    private static int measureTicks() {
        return AB.length > 0 ? AB_WINDOW * 12 : MEASURE_TICKS;
    }

    private static long rebuildsAtWindowStart;

    private static void sampleFps(Minecraft mc) {
        if (ticks <= WARMUP_TICKS) {
            return;
        }
        if (AB.length == 0) {
            if (ticks % 20 == 0) {
                fpsSamples.add(mc.getFps());
            }
            return;
        }
        int window = (ticks - WARMUP_TICKS - 1) / AB_WINDOW;
        int phase = (ticks - WARMUP_TICKS - 1) % AB_WINDOW;
        boolean on = (window % 2) == 0;
        if (phase == 0) {
            if (me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.TRACE_REBUILDS) {
                long total = me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.REBUILD_CALLERS.entrySet().stream().filter(e -> e.getKey().contains(System.getProperty("embeddium.traceRebuilds.filter", ""))).mapToLong(e -> e.getValue().sum()).sum();
                LOGGER.info("AUTOPROFILE window {} rebuild requests {}", window, total - rebuildsAtWindowStart);
                rebuildsAtWindowStart = total;
            }
            setAbFeatures(on);
        }
        // Skip the first two seconds of each window while the frame rate counter catches up
        if (phase >= 40 && phase % 20 == 0) {
            (on ? abFpsOn : abFpsOff).add(mc.getFps());
        }
    }

    private static void finishMeasurement(Minecraft mc) {
        if (me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.TRACE_REBUILDS) {
            me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager.REBUILD_CALLERS.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum())).limit(15)
                    .forEach(e -> LOGGER.info("AUTOPROFILE rebuilds {} from {}", e.getValue().sum(), e.getKey()));
        }
        if (AB.length > 0) {
            LOGGER.info("AUTOPROFILE ab {} on: avg={} {} | off: avg={} {}", String.join(",", AB),
                    String.format("%.1f", abFpsOn.stream().mapToInt(Integer::intValue).average().orElse(0)), abFpsOn,
                    String.format("%.1f", abFpsOff.stream().mapToInt(Integer::intValue).average().orElse(0)), abFpsOff);
        } else {
            logFps();
        }
        mc.stop();
    }

    private static boolean started;
    private static int ticks;
    private static final List<Integer> fpsSamples = new ArrayList<>();

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || started || !(event.getScreen() instanceof TitleScreen || event.getScreen() instanceof AccessibilityOnboardingScreen)) {
            return;
        }
        started = true;
        var mc = Minecraft.getInstance();
        mc.forceSetScreen(new GenericDirtMessageScreen(Component.literal("Bootstrapping benchmark...")));
        String levelName = "emberbium-bench";
        // The pack scenario reuses its world so later runs skip worldgen, which otherwise loads the server and workers
        if ((PACK || REBUILD) && mc.getLevelSource().levelExists(levelName)) {
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), levelName);
            return;
        }
        LevelSettings settings = new LevelSettings(levelName, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(levelName, settings, new WorldOptions(1024, false, false),
                registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(TERRAIN ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static boolean abFeatureOn = true;
    private static final List<Long> rebuildOn = new ArrayList<>();
    private static final List<Long> rebuildOff = new ArrayList<>();

    /**
     * The optimization being A/B tested by the rebuild benchmark; change this when measuring something else.
     */
    private static void setFeatureUnderTest(boolean enabled) {
        if (AB.length > 0) {
            setAbFeatures(enabled);
            return;
        }
        me.jellysquid.mods.sodium.client.SodiumClientMod.options().performance.useEnclosedBlockSkipping = enabled;
    }

    private static final List<Integer> distanceFpsOn = new ArrayList<>(), distanceFpsOff = new ArrayList<>();
    private static final List<Integer> distanceSectionsOn = new ArrayList<>(), distanceSectionsOff = new ArrayList<>();
    private static final List<Integer> distanceDrawsOn = new ArrayList<>(), distanceDrawsOff = new ArrayList<>();

    /**
     * The render distance optimization being A/B tested by the distance benchmark.
     */
    private static void setDistanceFeature(boolean enabled) {
        org.embeddedt.embeddium.compat.metusoblita.MetusOblitaRenderGate.enabled = enabled;
    }

    private static void tickDistance(Minecraft mc) {
        if (ticks == 20) {
            mc.options.renderDistance().set(24);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            // Uncapped so the measurement shows real frame cost rather than the limiter
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            cmd("gamerule doDaylightCycle false");
            cmd("time set noon");
            // Mountainous terrain makes vertical culling matter; stand above it
            cmd("tp @s 0 160 0");
        }
        if (ticks > 20) {
            // One slow full turn every 15 s, so each window sees every direction
            mc.player.setYRot((ticks % 300) * 1.2f);
            mc.player.setXRot(10.0f);
            mc.player.setPos(0.5, 160, 0.5);
        }
        // Let render distance 24 load fully before measuring
        int start = 1200;
        if (ticks < start) {
            return;
        }
        int window = (ticks - start) / 300;
        int phase = (ticks - start) % 300;
        boolean on = (window % 2) == 0;
        if (phase == 0) {
            setDistanceFeature(on && !Boolean.getBoolean("embeddium.autoProfile.spark"));
        }
        if (ticks == start && Boolean.getBoolean("embeddium.autoProfile.spark")) {
            cmd(Boolean.getBoolean("embeddium.autoProfile.serverSpark") ? "spark profiler start --thread * --timeout 60 --save-to-file" : "sparkc profiler start --thread * --timeout 60 --save-to-file");
        }
        if ((window == 2 || window == 3) && (phase == 60 || phase == 150 || phase == 240)) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "distance-" + phase + "-" + (on ? "ellipsoid" : "cylinder") + ".png",
                    mc.getMainRenderTarget(), msg -> LOGGER.info("AUTOPROFILE screenshot {}", msg.getString()));
        }
        // Skip the first second of each window while the visible set settles
        if (phase >= 20 && phase % 20 == 0) {
            var renderer = me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer.instanceNullable();
            (on ? distanceFpsOn : distanceFpsOff).add(mc.getFps());
            // Draw commands per frame over the last second
            long draws = me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer.DRAW_COMMANDS.sumThenReset();
            if (phase > 20) {
                (on ? distanceDrawsOn : distanceDrawsOff).add((int) (draws / Math.max(1, mc.getFps())));
            }
            if (renderer != null) {
                (on ? distanceSectionsOn : distanceSectionsOff).add(renderer.getVisibleChunkCount());
            }
        }
        if (window == 8) {
            LOGGER.info("AUTOPROFILE distance draws/frame on={} off={}",
                    distanceDrawsOn.stream().mapToInt(Integer::intValue).average().orElse(0),
                    distanceDrawsOff.stream().mapToInt(Integer::intValue).average().orElse(0));
            LOGGER.info("AUTOPROFILE distance fps on={} off={} | sections on={} off={}",
                    distanceFpsOn.stream().mapToInt(Integer::intValue).average().orElse(0),
                    distanceFpsOff.stream().mapToInt(Integer::intValue).average().orElse(0),
                    distanceSectionsOn.stream().mapToInt(Integer::intValue).average().orElse(0),
                    distanceSectionsOff.stream().mapToInt(Integer::intValue).average().orElse(0));
            mc.stop();
        }
    }

    private static void tickRebuild(Minecraft mc) {
        if (ticks == 20) {
            mc.options.renderDistance().set(12);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            cmd("gamerule doDaylightCycle false");
            cmd("time set noon");
            cmd("tp @s 0 120 0");
        }
        if (ticks > 20) {
            mc.player.setYRot(-90.0f);
            mc.player.setXRot(20.0f);
        }
        if (ticks < 400) {
            return;
        }
        // Each round: rebuild every chunk, give the builders a fixed window, then read average CPU time per section
        int round = (ticks - 400) / 200;
        int phase = (ticks - 400) % 200;
        if (phase == 190 && (round == 3 || round == 4)) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "rebuild-feature-" + abFeatureOn + ".png",
                    mc.getMainRenderTarget(), msg -> LOGGER.info("AUTOPROFILE screenshot {}", msg.getString()));
        }
        if (phase == 0) {
            if (round > 0) {
                long nanos = me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped.BUILD_NANOS.sumThenReset();
                long count = me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped.BUILD_COUNT.sumThenReset();
                if (round > 2) { // first rounds warm the JIT
                    long us = count == 0 ? 0 : nanos / count / 1000L;
                    (abFeatureOn ? rebuildOn : rebuildOff).add(us);
                    LOGGER.info("AUTOPROFILE rebuild round={} feature={} sections={} totalMs={} usPerSection={}", round, abFeatureOn, count, nanos / 1_000_000L, us);
                }
            } else {
                me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped.BUILD_NANOS.reset();
                me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped.BUILD_COUNT.reset();
            }
            if (round == 13) {
                LOGGER.info("AUTOPROFILE rebuild A/B on={} avg={} | off={} avg={}",
                        rebuildOn, rebuildOn.stream().mapToLong(Long::longValue).average().orElse(0),
                        rebuildOff, rebuildOff.stream().mapToLong(Long::longValue).average().orElse(0));
                mc.stop();
                return;
            }
            // Alternate the feature under test between rounds so both variants share one JVM and warm-up
            abFeatureOn = (round % 2) == 0;
            setFeatureUnderTest(abFeatureOn);
            mc.levelRenderer.allChanged();
        }
    }

    private static final String[] ENTITY_TYPES = {
            "minecraft:zombie", "minecraft:skeleton", "minecraft:villager", "minecraft:cow", "minecraft:sheep",
            "minecraft:creeper", "minecraft:spider", "minecraft:horse", "minecraft:pig", "minecraft:chicken",
            "minecraft:armor_stand", "minecraft:iron_golem", "alexsmobs:tiger", "alexsmobs:elephant", "alexsmobs:seal",
            "alexsmobs:toucan", "mutantmonsters:mutant_zombie", "minecraft:wolf", "minecraft:pillager", "minecraft:enderman"
    };

    private static void tickEntities(Minecraft mc) {
        if (ticks == 20) {
            mc.options.renderDistance().set(12);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            // 625 summon confirmations would otherwise keep the chat on screen through the measurement
            cmd("gamerule sendCommandFeedback false");
            cmd("difficulty easy");
            cmd("gamerule doMobSpawning false");
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doWeatherCycle false");
            cmd("weather clear 1000000");
            // Night, so undead don't burn away during the run
            cmd("time set 18000");
            cmd("tp @s 0 -52 0");
        }
        if (ticks >= 40 && ticks < 40 + 25) {
            // One row of a 25x25 grid per tick, two blocks apart
            int x = -24 + (ticks - 40) * 2;
            for (int i = 0; i < 25; i++) {
                int z = -24 + i * 2;
                String type = ENTITY_TYPES[Math.floorMod(x * 7 + z * 3, ENTITY_TYPES.length)];
                cmd("summon " + type + " " + x + " -60 " + z + " {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            }
        }
        if (ticks > 20) {
            mc.player.setPos(0.5, -52, 0.5);
            mc.player.setYRot((ticks * 1.5f) % 360.0f);
            mc.player.setXRot(25.0f);
        }
        if (ticks == WARMUP_TICKS) {
            LOGGER.info("AUTOPROFILE entities loaded={}", mc.level.getEntityCount());
            if (!Boolean.getBoolean("embeddium.autoProfile.noSpark")) {
                cmd("sparkc profiler start --timeout 60 --save-to-file");
            }
        }
        if (ticks == WARMUP_TICKS + 200) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "autoprofile-entities.png",
                    mc.getMainRenderTarget(), msg -> LOGGER.info("AUTOPROFILE screenshot {}", msg.getString()));
        }
        sampleFps(mc);
        if (ticks == WARMUP_TICKS + measureTicks()) {
            finishMeasurement(mc);
        }
    }

    private static double packX, packY, packZ;

    private static void tickPack(Minecraft mc) {
        if (ticks == 20) {
            mc.options.renderDistance().set(Integer.getInteger("embeddium.autoProfile.renderDistance", 12));
            // Uncapped so the measurement shows real frame cost rather than the limiter
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doWeatherCycle false");
            cmd("weather clear 1000000");
            cmd("time set noon");
            // Same spot every run, above the surface at world spawn, whether the world is fresh or reloaded
            var spawn = mc.level.getSharedSpawnPos();
            packX = spawn.getX() + 0.5;
            packZ = spawn.getZ() + 0.5;
            int surface = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
            packY = (surface > mc.level.getMinBuildHeight() ? surface : spawn.getY()) + 20.0;
        }
        if (ticks > 20) {
            // One full turn every 10 s, matching the A/B window so both variants see every direction
            // Walking pace circle (radius 3) so movement-driven work like entity culling passes runs too
            double angle = (ticks % AB_WINDOW) * (2.0 * Math.PI / AB_WINDOW);
            mc.player.setPos(packX + 3.0 * Math.cos(angle), packY, packZ + 3.0 * Math.sin(angle));
            mc.player.setYRot((ticks % AB_WINDOW) * (360.0f / AB_WINDOW));
            mc.player.setXRot(15.0f);
        }
        if (ticks == WARMUP_TICKS) {
            LOGGER.info("AUTOPROFILE pack entities={} pos={},{},{}", mc.level.getEntityCount(), packX, packY, packZ);
            if (!Boolean.getBoolean("embeddium.autoProfile.noSpark")) {
                cmd("sparkc profiler start --thread * --timeout 45 --save-to-file");
            }
        }
        if (ticks == WARMUP_TICKS + 100) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "autoprofile-pack.png",
                    mc.getMainRenderTarget(), msg -> LOGGER.info("AUTOPROFILE screenshot {}", msg.getString()));
        }
        sampleFps(mc);
        if (ticks == WARMUP_TICKS + (AB.length > 0 ? measureTicks() : 20 * 48)) {
            finishMeasurement(mc);
        }
    }

    private static void logFps() {
        List<Integer> sorted = new ArrayList<>(fpsSamples);
        sorted.sort(null);
        double avg = sorted.stream().mapToInt(Integer::intValue).average().orElse(0);
        LOGGER.info("AUTOPROFILE fps avg={} min={} p10={} max={} samples={}",
                String.format("%.1f", avg), sorted.get(0), sorted.get(sorted.size() / 10), sorted.get(sorted.size() - 1), sorted.size());
    }

    private static boolean isPosingForScreenshot() {
        return !TERRAIN && ticks >= WARMUP_TICKS - 60 && ticks <= WARMUP_TICKS - 20;
    }

    private static void cmd(String command) {
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (!ENABLED || event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        if (REBUILD) {
            tickRebuild(mc);
            return;
        }
        if (DISTANCE) {
            tickDistance(mc);
            return;
        }
        if (PACK) {
            tickPack(mc);
            return;
        }
        if (ENTITIES) {
            tickEntities(mc);
            return;
        }
        if (ticks == 20) {
            cmd("gamerule doDaylightCycle false");
            cmd("time set noon");
            // Random rain would change particle and fog load between runs
            cmd("gamerule doWeatherCycle false");
            cmd("weather clear 1000000");
            if (TERRAIN) {
                mc.options.renderDistance().set(16);
                cmd("tp @s 0 140 0");
            } else {
                cmd("tp @s 0 -45 0");
            }
        } else if (!TERRAIN && ticks >= 40 && ticks < 40 + 9) {
            // 9 layers of block entities in a 97x97 area around the player (~85k block entities)
            int i = ticks - 40;
            String[] blocks = {"chest", "shulker_box", "barrel", "chest", "ender_chest", "oak_sign", "chest", "enchanting_table", "trapped_chest"};
            int y = -60 + i * 3;
            cmd("fill -48 " + y + " -48 48 " + y + " 48 minecraft:" + blocks[i]);
        } else if (ticks == WARMUP_TICKS && !Boolean.getBoolean("embeddium.autoProfile.noSpark")) {
            cmd(TERRAIN ? "sparkc profiler start --thread * --timeout 60 --save-to-file" : "sparkc profiler start --timeout 60 --save-to-file");
        }
        if (TERRAIN && ticks > 60) {
            // Fly forward at 20 blocks/s so new chunks constantly load, mesh and upload
            mc.player.setYRot(-90.0f + (float) Math.sin(ticks / 40.0) * 30.0f);
            mc.player.setXRot(15.0f);
            mc.player.setPos(mc.player.getX() + 1.0, 140, mc.player.getZ());
        } else if (!TERRAIN && ticks > 20 && !isPosingForScreenshot()) {
            mc.player.setYRot(mc.player.getYRot() + 2.0f);
            mc.player.setXRot(20.0f);
        }
        if (ticks == 21) {
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            if (Boolean.getBoolean("embeddium.autoProfile.noBackfaceCull")) {
                me.jellysquid.mods.sodium.client.SodiumClientMod.options().performance.useModelBackfaceCulling = false;
            }
        }
        if (isPosingForScreenshot()) {
            // Fixed pose above the top layer so screenshots are comparable between runs
            mc.player.setPos(3.5, -33.0, 3.5);
            mc.player.setYRot(135.0f);
            mc.player.setXRot(40.0f);
        }
        if (ticks == WARMUP_TICKS - 20 || ticks == WARMUP_TICKS + 400) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "autoprofile-" + (TERRAIN ? "terrain" : "be") + "-" + ticks
                    + (Boolean.getBoolean("embeddium.autoProfile.noBackfaceCull") ? "-nocull" : "") + ".png",
                    mc.getMainRenderTarget(), msg -> LOGGER.info("AUTOPROFILE screenshot {}", msg.getString()));
        }
        sampleFps(mc);
        if (ticks == WARMUP_TICKS + measureTicks()) {
            finishMeasurement(mc);
        }
    }
}
