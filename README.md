# idlemate

Server-side Fabric mod that gives you a single fake-player slot for keeping AFK farms running on Polymer-using servers. Replaces Carpet's `/player spawn` — which crashes Polymer servers — with a focused, persistent, single-bot alternative.

Built for [The Bloc](https://thebloc.party). Open-source under MIT in case anyone else hits the same Carpet × Polymer crash.

## Why this exists

Carpet's fake players construct a synthetic connection that skips the login phase. Polymer's `polymer-core` mixin populates `polymer:holder_lookup` on the login listener's `PacketContext` for real players; fake-player connections never get it set, so the first chunk send hits `orElseThrow` and NPEs the server thread. Verified on MC 26.1.2 + Fabric Loader 0.19.1 + polymer-bundled 0.16.2+26.1.1.

This mod sets the same key (using polymer's actual `Key` instance via reflection — `PacketContext.Key` uses `IdentityHashMap`, equal Identifiers ≠ equal keys) on the synthetic `Connection`'s `PacketContext` *before* `placeNewPlayer` triggers chunk send. Polymer's wrapper then reads back what it expects, no NPE.

## Scope

Deliberately small ("Tier 1" — spawn-and-stand only):

- **Single fake player slot.** No multi-bot. Run `/fakeplayer kill` first if one is already active.
- **Stand-only.** No attack/walk/look/swap/dropall. The bot stands at the spawn coords until killed or restart-respawned.
- **Persists across restarts.** Spawn writes a JSON record next to the server jar; auto-respawns on `SERVER_STARTED` at the same coords + yaw/pitch.
- **Op-level-2 gated.** Matches Carpet's `/player` default and vanilla's `/give`-tier permission.
- **Hidden from tab list and SLP sample.** Real players don't see "BlocBot1" when they press TAB or ping the server externally. Still appears in `/list` (which reads `PlayerList` directly).

If you need attack-hold, walk paths, multiple bots, or any of Carpet's other 50 features, this isn't the mod for you. It's intentionally the smallest thing that solves "keep my creeper farm spawning while I'm asleep."

## Requirements

- Minecraft 26.1.x server (tested on 26.1.1 and 26.1.2)
- Fabric Loader ≥ 0.18.6 (tested on 0.19.1)
- Java 25
- [Fabric API](https://modrinth.com/mod/fabric-api) (any 26.1-compatible version)
- *Optional but recommended:* [polymer-bundled](https://modrinth.com/mod/polymer) — without it the mod still works, the polymer compat path becomes a no-op

## Install

1. Drop `idlemate-X.Y.Z.jar` into `<server>/mods/`
2. Restart the server
3. Op yourself in-game (or use RCON, which has implicit op level 4)

## Commands

```
/fakeplayer spawn [name]
    Spawn a fake player at the caller's location, copying yaw/pitch.
    Defaults to name "BlocBot1". Names must match [A-Za-z0-9_]{1,16}.

/fakeplayer spawn-at <x> <y> <z> [name]
    Console/RCON-friendly variant. Spawns at explicit coords in the
    caller's level. Useful for scripted bot deployment.

/fakeplayer kill
    Despawn the active fake player and delete the persistence record.

/fakeplayer info
    Print the active fake player's name and coords, or "no fake player
    is active" if none.
```

## Persistence

When you spawn a bot, the mod writes `<server-runDir>/idlemate.json`:

```json
{"name":"BlocBot1","dimension":"minecraft:overworld","x":146.75,"y":190.5,"z":325.37,"yaw":79.20278,"pitch":34.350147}
```

On `SERVER_STARTED` the mod reads this file and re-spawns the same bot at the same spot, automatically. `/fakeplayer kill` deletes the file.

If the file becomes invalid (unparseable JSON, missing dimension) the mod logs a warning, deletes the file, and skips the auto-respawn. The server **never crash-loops on a bad persistence record** — auto-respawn is best-effort, never load-bearing.

## Building

```sh
./gradlew build
# Output: build/libs/idlemate-0.1.0.jar
```

Requires Java 25 + a working internet connection (Loom downloads MC mappings on first build).

## Acknowledgments

- [gnembon/fabric-carpet](https://github.com/gnembon/fabric-carpet) — `EntityPlayerMPFake` and `FakeClientConnection` are the reference for synthetic player construction on Fabric. This mod's `FakeConnection` follows the same `EmbeddedChannel` + `setChannel` pattern.
- [Patbox/polymer](https://github.com/Patbox/polymer) — reading `polymer-common`'s `CommonImplPacketKeys` and the `ServerLoginPacketListenerImplMixin` was how I figured out the holder_lookup contract.
- [FabricMC](https://fabricmc.net) — the Networking API v1's `PacketContext` is the seam this whole mod lives in.

## License

MIT — see `LICENSE`.
