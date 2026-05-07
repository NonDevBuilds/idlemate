package party.thebloc.idlemate;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.TickTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import party.thebloc.idlemate.command.FakePlayerCommand;

public final class Idlemate implements DedicatedServerModInitializer {
	public static final String MOD_ID = "idlemate";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeServer() {
		FakePlayerCommand.register();
		// Auto-respawn the persisted fake player after server start. Runs after
		// dimensions are loaded but before players begin connecting, which is
		// what we want — chunks are ready, polymer is initialized.
		ServerLifecycleEvents.SERVER_STARTED.register(FakePlayerManager::tryRestoreFromPersistence);
		// Capture current position before graceful shutdown so a normal restart
		// preserves any drift (knockback, /tp, water flow). On crash this won't
		// fire; falls back to whatever was last persisted on spawn or /fakeplayer save.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (FakePlayerManager.savePosition(server).isPresent()) {
				LOG.info("Saved fake player position on shutdown.");
			}
		});
		// Hide the active fake player from each newly-joined player's tab list.
		// JOIN fires before vanilla sends the bulk player-info-update with the
		// full list (with bot listed=true). Schedule the Remove ~1s ahead so
		// the client processes Remove AFTER vanilla's initial list arrives.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.schedule(new TickTask(server.getTickCount() + 20,
						() -> FakePlayerManager.hideActiveFromJoiner(handler.player))));
		LOG.info("idlemate loaded.");
	}
}
