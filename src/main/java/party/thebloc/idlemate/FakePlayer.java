package party.thebloc.idlemate;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vanilla ServerPlayer except {@link #allowsListing()} returns false — the
 * fake player is internal infrastructure and shouldn't advertise to other
 * players' tab lists or to anonymous SLP pingers.
 *
 * `/list` and the bloc panel's authed `/api/status` will still surface the
 * fake player (they read PlayerList directly, not the listing-allowed
 * filter), which is the right behavior for ops introspecting their own
 * server.
 */
public final class FakePlayer extends ServerPlayer {
	public FakePlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation cli) {
		super(server, level, profile, cli);
	}

	@Override
	public boolean allowsListing() {
		return false;
	}
}
