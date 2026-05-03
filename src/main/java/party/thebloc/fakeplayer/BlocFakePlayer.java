package party.thebloc.fakeplayer;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import party.thebloc.fakeplayer.command.FakePlayerCommand;

public final class BlocFakePlayer implements DedicatedServerModInitializer {
	public static final String MOD_ID = "bloc_fakeplayer";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeServer() {
		FakePlayerCommand.register();
		LOG.info("bloc-fakeplayer loaded.");
	}
}
