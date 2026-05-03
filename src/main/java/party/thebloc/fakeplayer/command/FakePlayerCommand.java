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
import party.thebloc.fakeplayer.FakePlayerManager;

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
		CommandSourceStack src = ctx.getSource();
		String name = requestedName == null ? DEFAULT_NAME : requestedName;
		if (!NAME_PATTERN.matcher(name).matches()) {
			src.sendFailure(Component.literal(
					"Invalid name '" + name + "'. Must match [A-Za-z0-9_]{1,16}."));
			return 0;
		}

		var player = src.getPlayerOrException();
		var result = FakePlayerManager.spawn(
				src.getServer(),
				name,
				(net.minecraft.server.level.ServerLevel) player.level(),
				player.position(),
				player.getYRot(),
				player.getXRot());

		return switch (result) {
			case FakePlayerManager.SpawnResult.Spawned s -> {
				src.sendSuccess(() -> Component.literal(
						"Spawned fake player '" + s.name() + "'."), true);
				yield Command.SINGLE_SUCCESS;
			}
			case FakePlayerManager.SpawnResult.AlreadyActive a -> {
				src.sendFailure(Component.literal(
						"A fake player ('" + a.existingName()
								+ "') is already active. Run /fakeplayer kill first."));
				yield 0;
			}
		};
	}

	private static int kill(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		var result = FakePlayerManager.kill(src.getServer());
		return switch (result) {
			case FakePlayerManager.KillResult.Killed k -> {
				src.sendSuccess(() -> Component.literal(
						"Killed fake player '" + k.name() + "'."), true);
				yield Command.SINGLE_SUCCESS;
			}
			case FakePlayerManager.KillResult.NoneActive n -> {
				src.sendFailure(Component.literal("No fake player is active."));
				yield 0;
			}
		};
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		var info = FakePlayerManager.info(src.getServer());
		if (info.isEmpty()) {
			src.sendSuccess(() -> Component.literal("No fake player is active."), false);
		} else {
			var i = info.get();
			src.sendSuccess(() -> Component.literal(String.format(
					"Fake player '%s' at %.1f %.1f %.1f in %s",
					i.name(), i.position().x, i.position().y, i.position().z, i.dimension())), false);
		}
		return Command.SINGLE_SUCCESS;
	}
}
