package ar.edu.itba.it.gossip.proxy.xmpp.handler;

import static ar.edu.itba.it.gossip.proxy.xmpp.element.PartialXMPPElement.Type.STREAM_START;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.OriginToClientXMPPStreamHandler.State.AUTHENTICATED;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.OriginToClientXMPPStreamHandler.State.EXPECT_AUTH_FEATURES;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.OriginToClientXMPPStreamHandler.State.INITIAL;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.OriginToClientXMPPStreamHandler.State.LINKED;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.OriginToClientXMPPStreamHandler.State.VALIDATING_CREDENTIALS;

import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import ar.edu.itba.it.gossip.proxy.tcp.stream.ByteStream;
import ar.edu.itba.it.gossip.proxy.xmpp.XMPPConversation;
import ar.edu.itba.it.gossip.proxy.xmpp.element.AuthMechanism;
import ar.edu.itba.it.gossip.proxy.xmpp.element.PartialXMPPElement;
import ar.edu.itba.it.gossip.util.nio.ByteBufferOutputStream;

public class OriginToClientXMPPStreamHandler extends XMPPStreamHandler {
    private final XMPPConversation conversation;
    private final ByteStream originToClient;
    private final OutputStream toOrigin;

    private Set<String> authMechanisms;

    private State state = INITIAL;

    public OriginToClientXMPPStreamHandler(final XMPPConversation conversation,
            final ByteStream originToClient, final OutputStream toOrigin)
            throws XMLStreamException {
        this.conversation = conversation;
        this.toOrigin = toOrigin;
        this.originToClient = originToClient;

        authMechanisms = new HashSet<>();
    }

    @Override
    public void handleStart(PartialXMPPElement element) {
        switch (state) {
        case INITIAL:
            assumeType(element, STREAM_START);
            state = EXPECT_AUTH_FEATURES;
            break;
        case AUTHENTICATED:
            assumeType(element, STREAM_START);
            state = LINKED;
            System.out
                    .println("Origin is linked to the client, now messages may pass freely");

            sendDocumentStartToClient();
            // fall through
        case LINKED:
            sendToClient(element);
            break;
        default:
            // do nothing TODO: change this!
        }
    }

    @Override
    public void handleBody(PartialXMPPElement element) {
        if (state == LINKED) {
            sendToClient(element);
        }
    }

    @Override
    public void handleEnd(PartialXMPPElement element) {
        switch (state) { // FIXME: State pattern needed here!
        case EXPECT_AUTH_FEATURES:
            switch (element.getType()) {
            case AUTH_REGISTER:
            case AUTH_MECHANISMS:
                break;
            case AUTH_MECHANISM:
                authMechanisms.add(((AuthMechanism) element).getMechanism());
                break;
            case AUTH_FEATURES:
                sendAuthDataToOrigin();// FIXME: send the actual auth here!
                state = VALIDATING_CREDENTIALS;
                break;
            default:
                throw new IllegalStateException("Unexpected event type: "
                        + element.getType());
            }
            break;
        case VALIDATING_CREDENTIALS:
            switch (element.getType()) {
            case AUTH_SUCCESS:
                state = AUTHENTICATED;
                sendToClient(element);
                resetStream();
                break;
            // case AUTH_FAILURE://TODO
            default:
                throw new IllegalStateException("Unexpected event type: "
                        + element.getType());
            }
            break;
        case LINKED:
            sendToClient(element);
            break;
        default:
            // will never happen
            throw new IllegalStateException("Unexpected state" + state);
        }
    }

    private void sendAuthDataToOrigin() {
        writeTo(toOrigin,
                "<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"PLAIN\">"
                        + conversation.getCredentials().encode() + "</auth>");
    }

    private void sendDocumentStartToClient() {
        sendToClient("<?xml version=\"1.0\"?>");
    }

    private void sendToClient(PartialXMPPElement element) {
        System.out.println("\n<O2C sending to client>");
        String currentContent = element.serializeCurrentContent();
        System.out.println("Message:\n'"
                + StringEscapeUtils.escapeJava(currentContent) + "' (string) "
                + ArrayUtils.toString(currentContent.getBytes()));
        sendToClient(currentContent);
        System.out.println("\nOutgoing buffer afterwards:");
        ((ByteBufferOutputStream) originToClient.getOutputStream())
                .printBuffer(false, true, true);
        System.out.println("</O2C sending to client>\n");
    }

    private void sendToClient(String message) {
        writeTo(originToClient, message);
    }

    protected enum State {
        INITIAL, EXPECT_AUTH_FEATURES, VALIDATING_CREDENTIALS, AUTHENTICATED, LINKED;
    }
}
