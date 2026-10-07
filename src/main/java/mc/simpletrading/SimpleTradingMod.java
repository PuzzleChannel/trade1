/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Common entry point shared by the dedicated-server and client environments.
 *
 * <p>This file is based on the original Simple Trading project and has been
 * ported for Minecraft 26.2 Fabric. The original project is Apache-2.0.</p>
 */
public final class SimpleTradingMod {
    public static final String MOD_ID = "simpletrading";
    public static final Logger LOGGER = LogUtils.getLogger();

    private SimpleTradingMod() {
    }

    public static void init() {
        LOGGER.info("Simple Trading 26.2 initialized");
    }
}
