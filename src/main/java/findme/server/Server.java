package findme.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import static findme.server.LocationsHandler.pingAndCleanUpWebSockets;

public final class Server {

    public static void main(String[] args) throws Exception {
        // Configure the server.

        String OS = System.getProperty("os.name");
        Boolean isLinux = OS.equals("Linux");

        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;
        if (isLinux) {
            System.out.println("Using Epoll");
            bossGroup = new EpollEventLoopGroup(1);
            workerGroup = new EpollEventLoopGroup();
        } else {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
        }

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            if (isLinux) {
                b.group(bossGroup, workerGroup)
                        .channel(EpollServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new ServerInitializer());
            } else {
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new ServerInitializer());
            }
            
            Channel ch = b.bind(8500).sync().channel();

            pingAndCleanUpWebSockets();

            System.err.println("Open your web browser and navigate to ://127.0.0.1:8500/");

            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}