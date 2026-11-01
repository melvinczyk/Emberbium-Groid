package me.jellysquid.mods.sodium.client.compatibility.environment;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;

/**
 * Detects Intel integrated graphics and, once, lowers GPU-heavy vanilla settings that are above a conservative cap.
 * Settings are only ever lowered, never raised, and the user can change them afterwards freely.
 */
public class IntegratedGpuProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumClientMod.MODNAME + "-GpuProfile");

    private static volatile boolean intelIntegrated;

    public static void detect(GLContextInfo driver) {
        String vendor = driver.vendor().toLowerCase(Locale.ROOT);
        String renderer = driver.renderer().toLowerCase(Locale.ROOT);

        intelIntegrated = (vendor.contains("intel") || renderer.contains("intel"))
                && !renderer.contains("arc")
                && (renderer.contains("iris") || renderer.contains("uhd") || renderer.contains("hd graphics"));

        if (intelIntegrated) {
            LOGGER.warn("Intel integrated graphics detected ({}). If this machine has a dedicated GPU, set javaw.exe " +
                    "to use it in the OS graphics settings.", driver.renderer());
        }
    }

    public static void applyOnce() {
        var advanced = SodiumClientMod.options().advanced;

        if (!intelIntegrated || advanced.integratedGpuProfileApplied) {
            return;
        }

        Options options = Minecraft.getInstance().options;

        cap(options.renderDistance(), 10, "render distance");
        cap(options.simulationDistance(), 8, "simulation distance");
        cap(options.entityDistanceScaling(), 0.75, "entity distance");
        cap(options.biomeBlendRadius(), 1, "biome blend");

        if (options.cloudStatus().get() == CloudStatus.FANCY) {
            options.cloudStatus().set(CloudStatus.FAST);
        }
        if (options.particles().get() == ParticleStatus.ALL) {
            options.particles().set(ParticleStatus.DECREASED);
        }

        options.save();
        LOGGER.info("Applied low-end profile for integrated graphics (one time only; change settings freely)");

        advanced.integratedGpuProfileApplied = true;
        try {
            SodiumClientMod.options().writeChanges();
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private static <T extends Comparable<T>> void cap(net.minecraft.client.OptionInstance<T> option, T max, String name) {
        if (option.get().compareTo(max) > 0) {
            LOGGER.info("Lowering {} from {} to {}", name, option.get(), max);
            option.set(max);
        }
    }
}
