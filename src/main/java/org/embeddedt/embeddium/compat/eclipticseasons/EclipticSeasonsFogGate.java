package org.embeddedt.embeddium.compat.eclipticseasons;

/**
 * Switch for skipping Ecliptic Seasons' weather fog pass while it would be invisible; see FogRendererMixin.
 */
public final class EclipticSeasonsFogGate {
    // Toggled by the dev benchmark to A/B the gate
    public static boolean enabled = true;

    private EclipticSeasonsFogGate() {
    }
}
