package findme.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;

import java.util.Collections;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    public void initChannel(SocketChannel ch) {
        HttpServerCodec sourceCodec = new HttpServerCodec();
        HttpServerUpgradeHandler.UpgradeCodec upgradeCodec =
                new Http2ServerUpgradeCodec(new Http2Handler());
        HttpServerUpgradeHandler upgradeHandler =
                new HttpServerUpgradeHandler(sourceCodec, Collections.singletonList(upgradeCodec), 65536);

        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(sourceCodec);
        pipeline.addLast(upgradeHandler);
    }
}