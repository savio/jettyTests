package testjettys2;

/**
 * Created by smartin on 12/03/16.
 */

import org.eclipse.jetty.alpn.client.ALPNClientConnection;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.NegotiatingClientConnectionFactory;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class NlALPNClientConnectionFactory extends NegotiatingClientConnectionFactory {
	public final static String ALPN_NEGOTIATED_PROTOCOL = "ssl.alpn.protocol";

	private final Executor executor;
	private final List<String> protocols;
	private final String defaultProtocol;


	public NlALPNClientConnectionFactory(Executor executor, ClientConnectionFactory connectionFactory, List<String> protocols, String defaultProtocol) {
		super(connectionFactory);
		this.executor = executor;
		this.protocols = protocols;
		this.defaultProtocol = defaultProtocol;
		if (protocols.isEmpty())
			throw new IllegalArgumentException("ALPN protocol list cannot be empty");
	}

	@Override
	public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException {
		return new MyALPNClientConnection(endPoint, executor, getClientConnectionFactory(),
				(SSLEngine) context.get(SslClientConnectionFactory.SSL_ENGINE_CONTEXT_KEY), context, protocols, defaultProtocol);
	}


	static class MyALPNClientConnection extends ALPNClientConnection {
		final String defaultProtocol;
		Map<String, Object> context;

		public MyALPNClientConnection(EndPoint endPoint, Executor executor, ClientConnectionFactory connectionFactory, SSLEngine sslEngine, Map<String, Object> context, List<String> protocols, String defaultProtocol) {
			super(endPoint, executor, connectionFactory, sslEngine, context, protocols);
			this.defaultProtocol = defaultProtocol;
			this.context = context;
		}

		@Override
		public void unsupported() {
			context.put(ALPN_NEGOTIATED_PROTOCOL, defaultProtocol);
			super.unsupported();
		}

		@Override
		public void selected(String protocol) {
			context.put(ALPN_NEGOTIATED_PROTOCOL, protocol);
			super.selected(protocol);
		}

		@Override
		public List<String> protocols() {
			List<String> l = super.protocols();
			return l;
		}

	}
}