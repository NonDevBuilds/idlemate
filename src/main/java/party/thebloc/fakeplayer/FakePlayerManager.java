package party.thebloc.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Singleton state holder + lifecycle for the (single) fake player.
 *
 * Concurrency: spawn/kill are called from the server tick thread (Brigadier
 * command execution), so synchronization is not strictly required, but the
 * synchronized blocks make the invariant "at most one active fake" obvious
 * and cheap to enforce.
 */
public final class FakePlayerManager {
	private FakePlayerManager() {}

	private static volatile UUID activeId;
	private static volatile String activeName;

	public static synchronized SpawnResult spawn(MinecraftServer server, String name,
			ServerLevel level, Vec3 pos, float yaw, float pitch) {
		if (activeId != null) {
			return SpawnResult.alreadyActive(activeName);
		}
		UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
		GameProfile profile = new GameProfile(uuid, name);
		ClientInformation clientInfo = ClientInformation.createDefault();

		ServerPlayer player = new ServerPlayer(server, level, profile, clientInfo);
		player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);

		FakeConnection connection = new FakeConnection(PacketFlow.SERVERBOUND);
		CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, clientInfo, false);

		PlayerList list = server.getPlayerList();
		list.placeNewPlayer(connection, player, cookie);

		// Re-snap after placeNewPlayer (which may have re-positioned via stored
		// player data if a real player with this name ever existed).
		player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
		player.setHealth(20.0f);

		activeId = uuid;
		activeName = name;
		BlocFakePlayer.LOG.info("Spawned fake player '{}' at {} {} {} in {}",
				name, pos.x, pos.y, pos.z, level.dimension().identifier());
		return SpawnResult.spawned(uuid, name);
	}

	public static synchronized KillResult kill(MinecraftServer server) {
		UUID id = activeId;
		String name = activeName;
		if (id == null) {
			return KillResult.noneActive();
		}
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) {
			player.connection.onDisconnect(
					new DisconnectionDetails(Component.literal("[bloc_fakeplayer] killed")));
		}
		activeId = null;
		activeName = null;
		BlocFakePlayer.LOG.info("Killed fake player '{}'", name);
		return KillResult.killed(name);
	}

	public static synchronized Optional<Info> info(MinecraftServer server) {
		UUID id = activeId;
		if (id == null) return Optional.empty();
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			// Player was removed by some other path (server reload?) — clean up
			// our state and report as none.
			activeId = null;
			activeName = null;
			return Optional.empty();
		}
		return Optional.of(new Info(activeName, player.position(),
				player.level().dimension().identifier().toString()));
	}

	public record Info(String name, Vec3 position, String dimension) {}

	public sealed interface SpawnResult {
		record Spawned(UUID uuid, String name) implements SpawnResult {}
		record AlreadyActive(String existingName) implements SpawnResult {}

		static SpawnResult spawned(UUID uuid, String name) { return new Spawned(uuid, name); }
		static SpawnResult alreadyActive(String name) { return new AlreadyActive(name); }
	}

	public sealed interface KillResult {
		record Killed(String name) implements KillResult {}
		record NoneActive() implements KillResult {}

		static KillResult killed(String name) { return new Killed(name); }
		static KillResult noneActive() { return new NoneActive(); }
	}
}
