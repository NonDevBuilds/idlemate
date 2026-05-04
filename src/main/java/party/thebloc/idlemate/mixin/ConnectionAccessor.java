package party.thebloc.idlemate.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Connection's private `channel` field setter so we can install an
 * EmbeddedChannel on the synthetic FakeConnection. Without a non-null open
 * channel, Connection.isOpen() returns false and chunk-send paths skip the
 * fake player entirely (= no mob spawning around the fake player).
 *
 * Same pattern as Carpet's ClientConnectionInterface.
 */
@Mixin(Connection.class)
public interface ConnectionAccessor {
	@Accessor("channel")
	void idlemate$setChannel(Channel channel);
}
