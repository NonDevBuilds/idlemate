package party.thebloc.fakeplayer;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
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

	/** Polymer's per-connection registry lookup key. Setting this before
	 *  placeNewPlayer prevents the chunk-send NPE that crashes plain Carpet. */
	private static final PacketContext.Key<RegistryAccess> POLYMER_HOLDER_LOOKUP =
			PacketContext.key(Identifier.fromNamespaceAndPath("polymer", "holder_lookup"));

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

		ServerPlayer player = new FakePlayer(server, level, profile, clientInfo);
		player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);

		FakeConnection connection = new FakeConnection(PacketFlow.SERVERBOUND);
		CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, clientInfo, false);

		// Polymer compat: populate `polymer:holder_lookup` on the connection's
		// PacketContext BEFORE placeNewPlayer triggers chunk send. The
		// ServerCommonPacketListenerImpl's getPacketContext() delegates to the
		// connection's, so setting here covers both lookup paths.
		PacketContext ctx = ((PacketContextProvider) (Object) connection).getPacketContext();
		ctx.set(POLYMER_HOLDER_LOOKUP, server.registryAccess());
		BlocFakePlayer.LOG.info("Pre-spawn polymer holder_lookup set on connection (ctx={})", ctx);

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

		// Persist for auto-respawn on next server start.
		FakePlayerPersistence.save(server, new FakePlayerPersistence.Record(
				name,
				level.dimension().identifier().toString(),
				pos.x, pos.y, pos.z, yaw, pitch));

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
		FakePlayerPersistence.delete(server);
		BlocFakePlayer.LOG.info("Killed fake player '{}'", name);
		return KillResult.killed(name);
	}

	/**
	 * Auto-respawn the persisted fake player after a server restart.
	 * Best-effort: any failure (missing dimension, malformed file, etc.)
	 * logs a warning, deletes the stale record, and returns. Never throws.
	 */
	public static synchronized void tryRestoreFromPersistence(MinecraftServer server) {
		Optional<FakePlayerPersistence.Record> opt = FakePlayerPersistence.load(server);
		if (opt.isEmpty()) return;
		FakePlayerPersistence.Record r = opt.get();
		try {
			ResourceKey<Level> dimKey = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
					Identifier.parse(r.dimension()));
			ServerLevel level = server.getLevel(dimKey);
			if (level == null) {
				BlocFakePlayer.LOG.warn("[bloc-fakeplayer] persisted dimension '{}' not found; clearing record", r.dimension());
				FakePlayerPersistence.delete(server);
				return;
			}
			SpawnResult result = spawn(server, r.name(), level, new Vec3(r.x(), r.y(), r.z()), r.yaw(), r.pitch());
			if (result instanceof SpawnResult.Spawned) {
				BlocFakePlayer.LOG.info("Auto-respawned fake player '{}' from persistence", r.name());
			} else {
				BlocFakePlayer.LOG.warn("[bloc-fakeplayer] auto-respawn unexpected result: {}", result);
			}
		} catch (Exception e) {
			BlocFakePlayer.LOG.warn("[bloc-fakeplayer] auto-respawn failed ({}); clearing record", e.getMessage());
			FakePlayerPersistence.delete(server);
		}
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
