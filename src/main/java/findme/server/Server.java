package findme.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static findme.server.LocationsHandler.pingAndCleanUpWebSockets;

public final class Server {

    final static Logger logger = LoggerFactory.getLogger(Server.class);

    public static void main(String[] args) throws Exception {
        Boolean isLinux = System.getProperty("os.name").equals("Linux");

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
            if (isLinux) {
                b.group(bossGroup, workerGroup)
                        .channel(EpollServerSocketChannel.class)
                        .childHandler(new ServerInitializer());
            } else {
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new ServerInitializer());
            }

            pingAndCleanUpWebSockets();

            b.bind(8500).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}