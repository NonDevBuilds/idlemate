package party.thebloc.fakeplayer.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class FakePlayerCommand {
	private FakePlayerCommand() {}

	private static final String DEFAULT_NAME = "BlocBot1";
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			dispatcher.register(
				Commands.literal("fakeplayer")
					.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.literal("spawn")
						.executes(ctx -> spawn(ctx, null))
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name")))))
					.then(Commands.literal("kill")
						.executes(FakePlayerCommand::kill))
					.then(Commands.literal("info")
						.executes(FakePlayerCommand::info))
			);
		});
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, @Nullable String requestedName)
			throws CommandSyntaxException {
		String name = requestedName == null ? DEFAULT_NAME : requestedName;
		if (!NAME_PATTERN.matcher(name).matches()) {
			ctx.getSource().sendFailure(Component.literal(
				"Invalid name '" + name + "'. Must match [A-Za-z0-9_]{1,16}."));
			return 0;
		}
		ctx.getSource().sendSuccess(() ->
			Component.literal("[fakeplayer] spawn '" + name + "': not yet implemented (Phase 1 stub)."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int kill(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() ->
			Component.literal("[fakeplayer] kill: not yet implemented (Phase 1 stub)."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() ->
			Component.literal("[fakeplayer] info: no fake player active (Phase 1 stub)."), false);
		return Command.SINGLE_SUCCESS;
	}
}
