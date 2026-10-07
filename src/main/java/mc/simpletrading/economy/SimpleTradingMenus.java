package mc.simpletrading.economy;

import mc.simpletrading.SimpleTradingMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlagSet;

public final class SimpleTradingMenus {
    public static final MenuType<TradeChestMenu> TRADE = RegistryHelper.register();

    private SimpleTradingMenus() {
    }

    public static void init() {
        SimpleTradingMod.LOGGER.info("Simple Trading menu type registered");
    }

    private static final class RegistryHelper {
        private static MenuType<TradeChestMenu> register() {
            return net.minecraft.core.Registry.register(
                    BuiltInRegistries.MENU,
                    Identifier.fromNamespaceAndPath(SimpleTradingMod.MOD_ID, "trade"),
                    new MenuType<>(TradeChestMenu::new, FeatureFlagSet.of())
            );
        }
    }
}
