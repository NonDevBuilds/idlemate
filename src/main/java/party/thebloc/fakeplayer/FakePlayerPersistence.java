package party.thebloc.fakeplayer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists the active fake player across server restarts.
 *
 * Storage: a single JSON file at {server runDir}/bloc-fakeplayer.json. Written
 * on /fakeplayer spawn, deleted on /fakeplayer kill. Read once at
 * SERVER_STARTED and used to re-spawn the same fake player at the same
 * position automatically.
 *
 * Resilience: any read failure (missing/corrupt/missing-dimension) falls
 * through silently and the file is deleted so the server doesn't crashloop
 * on a bad record. Auto-respawn is best-effort, never load-bearing.
 */
public final class FakePlayerPersistence {
	private FakePlayerPersistence() {}

	private static final String FILE_NAME = "bloc-fakeplayer.json";
	private static final Gson GSON = new Gson();

	public record Record(String name, String dimension, double x, double y, double z, float yaw, float pitch) {}

	private static Path filePath(MinecraftServer server) {
		return server.getServerDirectory().resolve(FILE_NAME);
	}

	public static void save(MinecraftServer server, Record r) {
		try {
			Files.writeString(filePath(server), GSON.toJson(r));
		} catch (IOException e) {
			BlocFakePlayer.LOG.warn("[bloc-fakeplayer] failed to save persistence: {}", e.getMessage());
		}
	}

	public static void delete(MinecraftServer server) {
		try {
			Files.deleteIfExists(filePath(server));
		} catch (IOException e) {
			BlocFakePlayer.LOG.warn("[bloc-fakeplayer] failed to delete persistence: {}", e.getMessage());
		}
	}

	public static Optional<Record> load(MinecraftServer server) {
		Path p = filePath(server);
		if (!Files.exists(p)) return Optional.empty();
		try {
			String json = Files.readString(p);
			Record r = GSON.fromJson(json, Record.class);
			if (r == null || r.name == null || r.dimension == null) {
				BlocFakePlayer.LOG.warn("[bloc-fakeplayer] persistence file malformed; deleting");
				delete(server);
				return Optional.empty();
			}
			return Optional.of(r);
		} catch (IOException | JsonSyntaxException e) {
			BlocFakePlayer.LOG.warn("[bloc-fakeplayer] persistence load failed ({}); deleting", e.getMessage());
			delete(server);
			return Optional.empty();
		}
	}
}
