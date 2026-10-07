/* Modified from Rvhoyos/simple-trading for Minecraft 26.2 Fabric. */
package mc.simpletrading.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mc.simpletrading.economy.TradeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Registers and executes /trade. */
public final class TradeCommand {
    private TradeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trade")
                .executes(TradeCommand::showUsage)
                .then(Commands.literal("accept").executes(TradeCommand::acceptTrade))
                .then(Commands.literal("deny").executes(TradeCommand::denyTrade))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(TradeCommand::requestTrade)));
    }

    private static int showUsage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal("Использование: /trade <игрок>, /trade accept, /trade deny"),
                false
        );
        return 1;
    }

    private static int requestTrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer requester = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");

        if (requester == target) {
            requester.sendSystemMessage(Component.literal("§cНельзя обмениваться с самим собой."));
            return 0;
        }

        TradeManager.getInstance().requestTrade(requester, target);
        return 1;
    }

    private static int acceptTrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TradeManager.getInstance().acceptTrade(player, context.getSource().getServer());
        return 1;
    }

    private static int denyTrade(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TradeManager.getInstance().denyTrade(player, context.getSource().getServer());
        return 1;
    }
}
