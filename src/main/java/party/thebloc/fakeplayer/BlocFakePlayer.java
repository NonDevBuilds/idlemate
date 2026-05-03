package party.thebloc.fakeplayer;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import party.thebloc.fakeplayer.command.FakePlayerCommand;

public final class BlocFakePlayer implements DedicatedServerModInitializer {
	public static final String MOD_ID = "bloc_fakeplayer";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeServer() {
		FakePlayerCommand.register();
		// Auto-respawn the persisted fake player after server start. Runs after
		// dimensions are loaded but before players begin connecting, which is
		// what we want — chunks are ready, polymer is initialized.
		ServerLifecycleEvents.SERVER_STARTED.register(FakePlayerManager::tryRestoreFromPersistence);
		LOG.info("bloc-fakeplayer loaded.");
	}
}
