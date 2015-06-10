package ar.edu.itba.it.gossip.admin.tcp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ar.edu.itba.it.gossip.async.tcp.TCPEventHandler;
import ar.edu.itba.it.gossip.async.tcp.TCPReactor;
import ar.edu.itba.it.gossip.proxy.tcp.TCPStreamHandler;
import ar.edu.itba.it.gossip.util.nio.BufferUtils;

public abstract class TCPProxyAdmin implements TCPEventHandler {
    private final Logger logger = LoggerFactory.getLogger(TCPProxyAdmin.class);

    private final TCPReactor reactor;

    public TCPProxyAdmin(final TCPReactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public void handleAccept(SelectionKey key) {
        try {
            ServerSocketChannel listenChannel = (ServerSocketChannel) key
                    .channel();
            SocketChannel channel = listenChannel.accept();
            channel.configureBlocking(false); // Must be nonblocking to
                                              // register

            reactor.subscribe(channel, this);

            UnproxiedTCPConversation conversation = instanceConversation(channel);
            conversation.updateSubscription(key.selector());
        } catch (Exception ex) {
            logger.error(this + ": could not accept", ex);
        }
    }

    @Override
    public void handleRead(SelectionKey key) {
        // reactor.unsubscribe((SocketChannel) key.channel());
        SocketChannel channel = (SocketChannel) key.channel();
        UnproxiedTCPConversation conversation = (UnproxiedTCPConversation) key
                .attachment();
        try {

            ByteBuffer buffer = conversation.getReadBuffer();
            TCPStreamHandler handler = conversation.getHandler();

            int bytesRead = channel.read(buffer);

            // FIXME: just for debugging purposes
            String channelName = "admin";
            String bufferName = conversation.getBufferName(buffer);
            System.out.println("Read " + bytesRead + " bytes into '"
                    + bufferName + "' through '" + channelName + "Channel ("
                    + channel + "')");
            // FIXME: just for debugging purposes

            if (bytesRead == -1) {
                handler.handleEndOfInput();
                finish(conversation);
            } else if (bytesRead > 0) {
                buffer.flip();

                // FIXME: just for debugging purposes
                System.out.println(bufferName + "'s content: (JUST READ)"
                        + "\n===================\n" + BufferUtils.peek(buffer)
                        + "\n===================\n");
                // FIXME: just for debugging purposes

                handler.handleRead(buffer,
                        address -> connect(key, conversation, address));

                conversation.updateSubscription(key.selector());
            }
        } catch (Exception ex) {
            logger.error(this + ": error on read", ex);
            finish(conversation);
        }
    }

    private void connect(SelectionKey key,
            UnproxiedTCPConversation conversation, InetSocketAddress address) {
        try {
            SocketChannel origin = SocketChannel.open();
            origin.configureBlocking(false);
            origin.connect(address);

            reactor.subscribe(origin, this);
        } catch (Exception ex) {
            logger.error(this + ": could not connect", ex);
            finish(conversation);
        }
    }

    private void finish(UnproxiedTCPConversation conversation) {
        reactor.unsubscribe(conversation.getChannel());
        conversation.closeChannels();
    }

    @Override
    public void handleWrite(SelectionKey key) {
        UnproxiedTCPConversation conversation = (UnproxiedTCPConversation) key
                .attachment();
        try {
            SocketChannel channel = (SocketChannel) key.channel();

            ByteBuffer buffer = conversation.getWriteBuffer();

            // FIXME: just for debugging purposes
            String bufferName = conversation.getBufferName(buffer);
            String channelName = "admin";
            // FIXME: just for debugging purposes

            buffer.flip();
            // // FIXME: just for debugging purposes
            // System.out.println(bufferName + "'s content: (BEFORE WRITE)"
            // + "\n===================\n" + BufferUtils.peek(buffer)
            // + "\n===================\n");
            // // FIXME: just for debugging purposes

            int bytesWritten = channel.write(buffer);

            System.out.println("Wrote " + bytesWritten + " bytes from '"
                    + bufferName + "' through '" + channelName + "Channel ("
                    + channel + "')");

            // FIXME: just for debugging purposes
            System.out.println(bufferName + "'s content: (AFTER WRITE)"
                    + "\n===================\n" + BufferUtils.peek(buffer)
                    + "\n===================\n");
            // FIXME: just for debugging purposes

            buffer.compact(); // Make room for more data to be read in

            conversation.updateSubscription(key.selector());
            // reactor.unsubscribe((SocketChannel) key.channel());

            if (conversation.hasQuit()) {
                conversation.closeChannels();
            }
        } catch (Exception ex) {
            logger.error(this + ": error on write", ex);
            finish(conversation);
        }
    }

    @Override
    public void handleConnect(SelectionKey key) {
        // should do nothing, since this handler will never connect to sockets
    }

    protected abstract UnproxiedTCPConversation instanceConversation(
            SocketChannel channel);

    protected void closeAfterTimeout(SocketChannel channel, long millis) {
        reactor.closeAfterTimeout(channel, millis);
    }
}
