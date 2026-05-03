package party.thebloc.fakeplayer;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;
import party.thebloc.fakeplayer.mixin.ConnectionAccessor;

/**
 * Synthetic Connection for fake players. No network socket — just an
 * in-memory EmbeddedChannel so isOpen() returns true (otherwise chunk-send
 * paths skip the fake player and mob spawning never reaches them).
 *
 * All outbound packet sends are no-ops. Inbound packet handlers and
 * lifecycle setters are no-ops because nothing wires inbound packets to a
 * fake-player connection.
 *
 * Modeled on Carpet's FakeClientConnection.
 */
public final class FakeConnection extends Connection {
	public FakeConnection(PacketFlow flow) {
		super(flow);
		// EmbeddedChannel makes isOpen() return true. Required for chunk send
		// paths to actually try to deliver to this connection.
		// Cast via Object — javac doesn't see the mixin-applied interface at compile time.
		((ConnectionAccessor) (Object) this).bloc_fakeplayer$setChannel(new EmbeddedChannel());
	}

	@Override
	public void setReadOnly() {}

	@Override
	public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {}

	@Override
	public void handleDisconnection() {}

	@Override
	public void setListenerForServerboundHandshake(PacketListener listener) {}

	@Override
	public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {}
}
