package party.thebloc.fakeplayer;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlocFakePlayer implements DedicatedServerModInitializer {
	public static final String MOD_ID = "bloc_fakeplayer";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeServer() {
		LOG.info("bloc-fakeplayer loading (Phase 0 skeleton).");
	}
}
