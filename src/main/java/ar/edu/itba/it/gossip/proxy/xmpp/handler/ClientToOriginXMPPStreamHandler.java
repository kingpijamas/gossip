package ar.edu.itba.it.gossip.proxy.xmpp.handler;

import static ar.edu.itba.it.gossip.proxy.xmpp.handler.ClientToOriginXMPPStreamHandler.AuthState.AUTHENTICATED;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.ClientToOriginXMPPStreamHandler.AuthState.AUTHENTICATING;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.ClientToOriginXMPPStreamHandler.AuthState.CONFIRMED;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.ClientToOriginXMPPStreamHandler.AuthState.NEGOTIATING;
import static ar.edu.itba.it.gossip.proxy.xmpp.handler.ClientToOriginXMPPStreamHandler.AuthState.OPEN;
import static ar.edu.itba.it.gossip.util.Validations.assumeState;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import javax.xml.stream.XMLStreamException;

import ar.edu.itba.it.gossip.proxy.xml.XMLStreamHandler;
import ar.edu.itba.it.gossip.proxy.xmpp.event.AuthStanza;
import ar.edu.itba.it.gossip.proxy.xmpp.event.XMPPEvent;

public class ClientToOriginXMPPStreamHandler extends XMLStreamHandler {
    private static final String PLAIN_AUTH = "PLAIN";

    private final OutputStream toClient;
    private final OutputStream toOrigin;

    private String username;
    private String password;

    private AuthState authState = AuthState.INITIAL;

    public ClientToOriginXMPPStreamHandler(final OutputStream toClient,
            final OutputStream toOrigin) throws XMLStreamException {
        this.toClient = toClient;
        this.toOrigin = toOrigin;

        setEventHandler(new StanzaEventHandler(this));
    }

    @Override
    public void handle(XMPPEvent event) {
        switch (authState) {
        case INITIAL:
            assumeEventType(event, XMPPEvent.Type.START_STREAM);
            sendStreamOpenToClient();
            sendStreamFeaturesToClient();
            authState = NEGOTIATING;
            break;
        case NEGOTIATING:
            assumeEventType(event, XMPPEvent.Type.AUTH);
            AuthStanza auth = (AuthStanza) event;
            assumeState(auth.mechanismMatches(PLAIN_AUTH),
                    "Auth mechanism not supported: %s");
            username = auth.getUsername();
            password = auth.getPassword();
            System.out.println(username
                    + " is trying to log in with password: " + password);

            InetSocketAddress address = getOriginAddressForUsername(username);
            getConnector().connectToOrigin(address);

            sendStreamOpenToOrigin();

            authState = AUTHENTICATING;
            break;
        case AUTHENTICATING:
            assumeEventType(event, XMPPEvent.Type.RESPONSE);
            sendAuthChallengeResponse();
            authState = AUTHENTICATED;
            break;
        case AUTHENTICATED:
            assumeEventType(event, XMPPEvent.Type.RESPONSE);
            sendAuthSuccess();
            authState = CONFIRMED;
            resetStream();
            break;
        case CONFIRMED:
            assumeEventType(event, XMPPEvent.Type.START_STREAM);
            // sendStreamOpen();
            // sendAuthenticatedStreamFeatures();
            System.out.println(username + " logged in with password: "
                    + password);

            authState = OPEN;
            break;
        case OPEN:
            break;
        }
    }

    private void sendStreamOpenToOrigin() {
        try {
            toOrigin.write("<?xml version=\"1.0\"?><stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xmlns=\"jabber:client\" to=\"example.com\" xml:lang=\"en\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\">"
                    .getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendStreamOpenToClient() {
        try {
            toClient.write("<?xml version=\"1.0\"?><stream:stream xmlns:stream='http://etherx.jabber.org/streams' version='1.0' from='localhost' id='6e5bb830-1e2d-40c3-8ebf-eacec740d83b' xml:lang='en' xmlns='jabber:toClient'>"
                    .getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendStreamFeaturesToClient() {
        try {
            toClient.write(("<stream:features>\n"
                    + "<register xmlns=\"http://jabber.org/features/iq-register\"/>\n"
                    + "<mechanisms xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">\n"
                    + "<mechanism>PLAIN</mechanism>\n" + "</mechanisms>\n"
                    + "</stream:features>").getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendAuthenticatedStreamFeatures() {
        try {
            toClient.write(("<stream:features>\n"
                    + "<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\">\n"
                    + "<required/>\n"
                    + "</bind>\n"
                    + "<session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\">\n"
                    + "<optional/>\n"
                    + "</session>\n"
                    + "<ver xmlns=\"urn:xmpp:features:rosterver\"/>\n"
                    + "<c xmlns=\"http://jabber.org/protocol/caps\" node=\"http://prosody.im\" ver=\"ZBWApSGFMsTZkuVThHtyU5xv1Mk=\" hash=\"sha-1\"/>\n"
                    + "</stream:features>").getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendAuthChallenge() {
        try {
            toClient.write("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">cmVhbG09ImxvY2FsaG9zdCIsbm9uY2U9IjA4NmQzNTJmLTY3YWMtNDU0MS1iNzA0LTQ1MmQ5ZjViNjRmMiIscW9wPSJhdXRoIixjaGFyc2V0PXV0Zi04LGFsZ29yaXRobT1tZDUtc2Vzcw==</challenge>"
                    .getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendAuthChallengeResponse() {
        try {
            toClient.write("<challenge xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">cnNwYXV0aD1lZTAwMzkyZDlhYTg2NmYzMDFhM2U0MjI4MzFkYTYwOQ==</challenge>"
                    .getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendAuthSuccess() {
        try {
            toClient.write("<success xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>"
                    .getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InetSocketAddress getOriginAddressForUsername(String username) {
        return new InetSocketAddress("localhost", 5222);
    }

    protected enum AuthState {
        INITIAL, NEGOTIATING, AUTHENTICATING, AUTHENTICATED, OPEN, CONFIRMED
    }
}