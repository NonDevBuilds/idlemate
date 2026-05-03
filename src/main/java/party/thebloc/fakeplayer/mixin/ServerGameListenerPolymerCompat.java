package party.thebloc.fakeplayer.mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Polymer compatibility shim — populates `polymer:holder_lookup` on the game
 * listener's PacketContext at construction TAIL, BEFORE any chunk-send
 * machinery downstream in PlayerList.placeNewPlayer can NPE on it.
 *
 * Mirrors polymer's own ServerLoginPacketListenerImplMixin pattern but
 * targets the game listener — that's where our fake players land directly
 * (we skip login). Real players are unaffected: polymer's login mixin sets
 * the same key on a different listener earlier; this mixin re-sets the same
 * value (server.registryAccess()) on the game listener, which is a no-op for
 * downstream code.
 *
 * The key is constructed by Identifier so we don't import polymer internals;
 * if polymer isn't on the server, the key is registered but no consumer
 * reads it — pure no-op overhead.
 *
 * Background: see PLAN.md "Phase 0 research findings".
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGameListenerPolymerCompat implements PacketContextProvider {
	private static final PacketContext.Key<RegistryAccess> POLYMER_HOLDER_LOOKUP =
			PacketContext.key(Identifier.fromNamespaceAndPath("polymer", "holder_lookup"));

	@Inject(method = "<init>", at = @At("TAIL"))
	private void bloc_fakeplayer$populatePolymerContext(
			MinecraftServer server, Connection connection, ServerPlayer player,
			CommonListenerCookie cookie, CallbackInfo ci) {
		this.getPacketContext().set(POLYMER_HOLDER_LOOKUP, server.registryAccess());
	}
}
