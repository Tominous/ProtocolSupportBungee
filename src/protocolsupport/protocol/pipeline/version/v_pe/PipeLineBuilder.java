package protocolsupport.protocol.pipeline.version.v_pe;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.connection.UpstreamBridge;
import net.md_5.bungee.netty.PipelineUtils;
import protocolsupport.api.Connection;
import protocolsupport.injector.BungeeNettyChannelInjector.CustomHandlerBoss;
import protocolsupport.protocol.packet.handler.EntityRewriteDownstreamBridge;
import protocolsupport.protocol.packet.handler.EntityRewriteUpstreamBridge;
import protocolsupport.protocol.pipeline.IPipeLineBuilder;
import protocolsupport.protocol.pipeline.common.EncapsulatedConnectionKeepAlive;
import protocolsupport.protocol.pipeline.common.EncapsulatedHandshakeSender;
import protocolsupport.protocol.pipeline.common.PacketCompressor;
import protocolsupport.protocol.pipeline.common.PacketDecompressor;
import protocolsupport.protocol.storage.NetworkDataCache;
import protocolsupport.utils.ReflectionUtils;

public class PipeLineBuilder extends IPipeLineBuilder {

	@Override
	public void buildBungeeClientCodec(Channel channel, Connection connection) {
		ChannelPipeline pipeline = channel.pipeline();
		NetworkDataCache cache = new NetworkDataCache();
		cache.storeIn(connection);
		pipeline.replace(PipelineUtils.PACKET_DECODER, PipelineUtils.PACKET_DECODER, new FromClientPacketDecoder(connection, cache));
		pipeline.replace(PipelineUtils.PACKET_ENCODER, PipelineUtils.PACKET_ENCODER, new ToClientPacketEncoder(connection, cache));
		pipeline.get(CustomHandlerBoss.class).setHandlerChangeListener((handler) -> {
			try {
				return (handler instanceof UpstreamBridge) ? new EntityRewriteUpstreamBridge(
					ReflectionUtils.getFieldValue(handler, "con"), connection.getVersion()
				) : handler;
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void buildBungeeClientPipeLine(Channel channel, Connection connection) {
		throw new UnsupportedOperationException("Only connection through encapsulation protocol is supported");
	}

	@Override
	public void buildBungeeServer(Channel channel, Connection connection) {
		ChannelPipeline pipeline = channel.pipeline();
		pipeline.addFirst("ps-encap-hs-sender", new EncapsulatedHandshakeSender(null, true));
		pipeline.addAfter("ps-encap-hs-sender", "keepalive", new EncapsulatedConnectionKeepAlive());
		NetworkDataCache cache = NetworkDataCache.getFrom(connection);
		pipeline.replace(PipelineUtils.PACKET_DECODER, PipelineUtils.PACKET_DECODER, new FromServerPacketDecoder(connection, cache));
		pipeline.replace(PipelineUtils.PACKET_ENCODER, PipelineUtils.PACKET_ENCODER, new ToServerPacketEncoder(connection, cache));
		pipeline.addAfter(PipelineUtils.FRAME_PREPENDER, "compress", new PacketCompressor(256));
		pipeline.addAfter(PipelineUtils.FRAME_DECODER, "decompress", new PacketDecompressor());
		pipeline.get(CustomHandlerBoss.class).setHandlerChangeListener(handler -> {
			try {
				return (handler instanceof DownstreamBridge) ? new EntityRewriteDownstreamBridge(
					ReflectionUtils.getFieldValue(handler, "con"), connection.getVersion()
				) : handler;
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		});
	}

}
