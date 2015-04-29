package findme.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.*;

/**
 * Created by daniel on 4/29/15.
 */
public class Http2Handler extends Http2ConnectionHandler {

    public Http2Handler() {
        this(new DefaultHttp2Connection(true), new Http2FrameListener());
    }

    private Http2Handler(Http2Connection connection, Http2FrameListener listener) {
        super(connection, listener);
        listener.encoder(encoder());
    }

    /**
     * Handles the cleartext HTTP upgrade event. If an upgrade occurred, sends a simple response via HTTP/2
     * on stream 1 (the stream specifically reserved for cleartext HTTP upgrade).
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpServerUpgradeHandler.UpgradeEvent) {
            // Write an HTTP/2 response to the upgrade request
            Http2Headers headers =
                    new DefaultHttp2Headers().status(OK.codeAsText())
                            .set(new AsciiString(UPGRADE_RESPONSE_HEADER), new AsciiString("true"));
            encoder().writeHeaders(ctx, 1, headers, 0, true, ctx.newPromise());
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    private static class Http2FrameListener extends Http2FrameAdapter {
        private Http2ConnectionEncoder encoder;

        public void encoder(Http2ConnectionEncoder encoder) {
            this.encoder = encoder;
        }

        /**
         * If receive a frame with end-of-stream set, send a pre-canned response.
         */
        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                              boolean endOfStream) throws Http2Exception {
            int processed = data.readableBytes() + padding;
            if (endOfStream) {
                sendResponse(ctx, streamId, data.retain());
            }
            return processed;
        }

        /**
         * If receive a frame with end-of-stream set, send a pre-canned response.
         */
        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                                  Http2Headers headers, int streamDependency, short weight,
                                  boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            if (endStream) {
                sendResponse(ctx, streamId, RESPONSE_BYTES.duplicate());
            }
        }

        /**
         * Sends a "Hello World" DATA frame to the client.
         */
        private void sendResponse(ChannelHandlerContext ctx, int streamId, ByteBuf payload) {
            // Send a frame for the response status
            Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
            encoder.writeHeaders(ctx, streamId, headers, 0, false, ctx.newPromise());
            encoder.writeData(ctx, streamId, payload, 0, true, ctx.newPromise());
            ctx.flush();
        }
    }




}
