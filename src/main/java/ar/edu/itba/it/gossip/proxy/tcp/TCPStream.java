package ar.edu.itba.it.gossip.proxy.tcp;

import static ar.edu.itba.it.gossip.util.ValidationUtils.assumeNotSet;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import ar.edu.itba.it.gossip.util.nio.BufferUtils;
import ar.edu.itba.it.gossip.util.nio.ByteBufferInputStream;
import ar.edu.itba.it.gossip.util.nio.ByteBufferOutputStream;
import ar.edu.itba.it.gossip.util.nio.ByteStream;

public class TCPStream extends ByteStream {
    private static final int BUF_SIZE = 4 * 1024;

    private final Endpoint from;
    private final Endpoint to;

    private final ChannelTerminator channelTerminator;

    private TCPStreamHandler handler;// TODO: check!

    private boolean allowInflow = true;

    public TCPStream(final SocketChannel fromChannel,
            final SocketChannel toChannel, final ChannelTerminator terminator) {
        this.from = new Endpoint(fromChannel);
        this.to = new Endpoint(toChannel);
        this.channelTerminator = terminator;
    }

    public void setHandler(final TCPStreamHandler handler) {
        assumeNotSet(this.handler, "Handler already set: %s");
        this.handler = handler;
    }

    public TCPStreamHandler getHandler() {
        return handler;
    }

    public SocketChannel getFromChannel() {
        return from.channel;
    }

    public SocketChannel getToChannel() {
        return to.channel;
    }

    public ByteBuffer getFromBuffer() {
        return from.buffer;
    }

    public ByteBuffer getToBuffer() {
        return to.buffer;
    }

    public InputStream getInputStream() {
        return new ByteBufferInputStream(getFromBuffer());
    }

    /*
     * That is, where data would be written *into* the Stream by someone outside
     * the normal flow (i.e.: a proxy).
     * 
     * IMPORTANT: note that the data written here WILL BE THE ONLY DATA THAT
     * WILL FLOW OUTSIDE THE STREAM.
     */
    public OutputStream getOutputStream() {
        return new ByteBufferOutputStream(getToBuffer());
    }

    public int getFromSubscriptionFlags() {
        if (allowInflow && getFromBuffer().hasRemaining()) {
            return SelectionKey.OP_READ;
        }
        return 0;
    }

    public int getToSubscriptionFlags() {
        return getToBuffer().position() > 0 ? SelectionKey.OP_WRITE : 0;
    }

    public void setToChannel(final SocketChannel channel) {
        assumeNotSet(getToChannel(), "Channel already set: %s");
        to.channel = channel;
    }

    public void setFromChannel(final SocketChannel channel) {
        assumeNotSet(getFromChannel(), "Channel already set: %s");
        from.channel = channel;
    }

    @Override
    public void pauseInflow() {
        this.allowInflow = false;
    }

    @Override
    public void resumeInflow() {
        this.allowInflow = true;
    }

    public void endInflowAfterTimeout() {
        this.allowInflow = false;
        channelTerminator.closeAfterTimeout(getFromChannel());
    }

    @Override
    // moves data safely from fromBuffer to toBuffer
    public void flush() {
        BufferUtils.transfer(getFromBuffer(), getToBuffer());
    }

    private static class Endpoint {
        SocketChannel channel;
        final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);

        Endpoint(final SocketChannel channel) { // NOTE: null is acceptable
                                                // here!
            this.channel = channel;
        }

        @Override
        public String toString() {
            return reflectionToString(this);
        }
    }
}