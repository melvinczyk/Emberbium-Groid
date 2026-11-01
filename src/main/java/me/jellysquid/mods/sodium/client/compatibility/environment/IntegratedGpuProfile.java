package me.jellysquid.mods.sodium.client.compatibility.environment;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Detects Intel integrated graphics so the renderer can pick paths that suit them. Nothing here changes the player's
 * saved settings or any vanilla option; overrides are applied at runtime and can be turned off in the config.
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
            LOGGER.warn("Intel integrated graphics detected ({}). Using integrated-GPU render paths. If this machine " +
                    "has a dedicated GPU, set javaw.exe to use it in the OS graphics settings.", driver.renderer());
        }
    }

    public static boolean isActive() {
        return intelIntegrated && SodiumClientMod.options().advanced.autoTuneIntegratedGpu;
    }
}
