package party.thebloc.idlemate;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
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

import java.util.List;

import java.lang.reflect.Field;
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

	/**
	 * Polymer's per-connection registry lookup key.
	 *
	 * CRITICAL: must use polymer's ACTUAL Key instance (not a fresh
	 * PacketContext.key(Identifier "polymer:holder_lookup") with the same id),
	 * because PacketContextImpl uses an IdentityHashMap — Keys with equal
	 * Identifiers but different object identities are treated as separate
	 * entries. Reflecting polymer's static field gives us the exact instance
	 * that polymer's BlockEntityInfo mixin reads via orElseThrow.
	 *
	 * If polymer isn't on the classpath (mod running on a vanilla-Fabric
	 * server), this is null and we skip the set — no harm, no NPE since the
	 * polymer mixin isn't present either.
	 */
	@SuppressWarnings("unchecked")
	private static final PacketContext.Key<RegistryAccess> POLYMER_HOLDER_LOOKUP = resolvePolymerHolderLookup();

	private static PacketContext.Key<RegistryAccess> resolvePolymerHolderLookup() {
		try {
			Class<?> keys = Class.forName("eu.pb4.polymer.common.impl.CommonImplPacketKeys");
			Field f = keys.getField("HOLDER_LOOKUP");
			Object value = f.get(null);
			Idlemate.LOG.info("Resolved polymer HOLDER_LOOKUP key: {}", value);
			return (PacketContext.Key<RegistryAccess>) value;
		} catch (Throwable e) {
			Idlemate.LOG.info("Polymer not present on classpath, skipping holder_lookup set: {}", e.toString());
			return null;
		}
	}

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
		// PacketContext BEFORE placeNewPlayer triggers chunk send. Listener's
		// getPacketContext() delegates to connection's, so setting once covers
		// both. Skip silently if polymer isn't loaded — server will still work,
		// no NPE because polymer's mixin isn't present either.
		if (POLYMER_HOLDER_LOOKUP != null) {
			PacketContext ctx = ((PacketContextProvider) (Object) connection).getPacketContext();
			ctx.set(POLYMER_HOLDER_LOOKUP, server.registryAccess());
			Idlemate.LOG.info("Pre-spawn polymer holder_lookup set on connection (ctx={})", ctx);
		}

		PlayerList list = server.getPlayerList();
		list.placeNewPlayer(connection, player, cookie);

		// Re-snap after placeNewPlayer (which may have re-positioned via stored
		// player data if a real player with this name ever existed).
		player.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
		player.setHealth(20.0f);

		// Hide from tab list. ServerPlayer.allowsListing() in 26.1 only gates
		// the SLP sample list — Entry(ServerPlayer) ctor hardcodes listed=true
		// regardless. Send a follow-up Remove packet so the bot doesn't show
		// up in connected players' tab lists.
		hideFromTabList(server, uuid);

		activeId = uuid;
		activeName = name;
		Idlemate.LOG.info("Spawned fake player '{}' at {} {} {} in {}",
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
					new DisconnectionDetails(Component.literal("[idlemate] killed")));
		}
		activeId = null;
		activeName = null;
		FakePlayerPersistence.delete(server);
		Idlemate.LOG.info("Killed fake player '{}'", name);
		return KillResult.killed(name);
	}

	/** Broadcast a Remove packet to hide the given UUID from every connected player's tab list. */
	private static void hideFromTabList(MinecraftServer server, java.util.UUID uuid) {
		ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(uuid));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.getUUID().equals(uuid)) {
				p.connection.send(packet);
			}
		}
	}

	/** Send a tab-list remove for the active fake player to a single newly-joined player. */
	public static void hideActiveFromJoiner(ServerPlayer joiner) {
		java.util.UUID id = activeId;
		if (id == null || id.equals(joiner.getUUID())) return;
		joiner.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(id)));
		Idlemate.LOG.info("[idlemate] sent tab-list-remove({}) to {}", id, joiner.getName().getString());
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
				Idlemate.LOG.warn("[idlemate] persisted dimension '{}' not found; clearing record", r.dimension());
				FakePlayerPersistence.delete(server);
				return;
			}
			SpawnResult result = spawn(server, r.name(), level, new Vec3(r.x(), r.y(), r.z()), r.yaw(), r.pitch());
			if (result instanceof SpawnResult.Spawned) {
				Idlemate.LOG.info("Auto-respawned fake player '{}' from persistence", r.name());
			} else {
				Idlemate.LOG.warn("[idlemate] auto-respawn unexpected result: {}", result);
			}
		} catch (Exception e) {
			Idlemate.LOG.warn("[idlemate] auto-respawn failed ({}); clearing record", e.getMessage());
			FakePlayerPersistence.delete(server);
		}
	}

	/**
	 * Capture the active fake player's current position into persistence.
	 * Used by /fakeplayer save (manual) and SERVER_STOPPING (graceful shutdown)
	 * to preserve drift from knockback / /tp / water flow / etc.
	 *
	 * Returns the snapshot that was saved, or empty if no fake player is active.
	 */
	public static synchronized Optional<Info> savePosition(MinecraftServer server) {
		Optional<Info> current = info(server);
		if (current.isEmpty()) return Optional.empty();
		Info i = current.get();
		ServerPlayer player = server.getPlayerList().getPlayer(activeId);
		float yaw = player != null ? player.getYRot() : 0f;
		float pitch = player != null ? player.getXRot() : 0f;
		FakePlayerPersistence.save(server, new FakePlayerPersistence.Record(
				i.name(), i.dimension(), i.position().x, i.position().y, i.position().z, yaw, pitch));
		return current;
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
