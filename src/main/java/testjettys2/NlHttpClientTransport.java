package testjettys2;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpDestinationOverHTTP;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http2.client.HTTP2ClientConnectionFactory;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Created by smartin on 18/03/16.
 */
public class NlHttpClientTransport extends ContainerLifeCycle implements HttpClientTransport {
	private HttpClient httpClient;
	protected SelectorManager selectorManager;

	// HTTP1
	private final HttpClientTransportOverHTTP http1Transport = new HttpClientTransportOverHTTP();

	// HTTP2
	private final Http2InitiatorHelper http2InitiatorHelper;

	public NlHttpClientTransport() throws Exception {
		http2InitiatorHelper = new Http2InitiatorHelper();

	}

	@Override
	public void setHttpClient(final HttpClient client) {
		this.httpClient = client;
		http1Transport.setHttpClient(client);
		client.addBean(http1Transport);

		selectorManager = new MySelector(httpClient.getExecutor(), httpClient.getScheduler());
		http2InitiatorHelper.setHttpClient(httpClient);
	}

	@Override
	protected void doStart() throws Exception {
		http1Transport.start();
		http2InitiatorHelper.start();
		selectorManager.start();

	}


	@Override
	public HttpDestination newHttpDestination(Origin origin) {
		if (HttpScheme.HTTPS.is(origin.getScheme())) {
			return new NlUniversalHttpDestination(httpClient, origin, this, http1Transport, http2InitiatorHelper);
		}
		return new HttpDestinationOverHTTP(httpClient, origin);
	}

	@Override
	public void connect(InetSocketAddress address, Map<String, Object> context) {
		if (context == null) {
			context = new HashMap<>();
		}

		try {
			SocketChannel channel = SocketChannel.open();
			channel.socket().setTcpNoDelay(true);
			channel.configureBlocking(false);

			//HttpDestination destination = (HttpDestination)context.get(HTTP_DESTINATION_CONTEXT_KEY);
			//HttpClient client = destination.getHttpClient();
			if (channel.connect(address))
				selectorManager.accept(channel, context);
			else
				selectorManager.connect(channel, context);
		} catch (IOException e) {
			System.out.println(e);
			e.printStackTrace();
		}
	}

	@Override
	public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException {
		HttpDestination hd = (HttpDestination) context.get(HTTP_DESTINATION_CONTEXT_KEY);
		return hd.getClientConnectionFactory().newConnection(endPoint, context);
	}


	public static class MySelector extends SelectorManager {

		protected MySelector(Executor executor, Scheduler scheduler) {
			super(executor, scheduler);
		}

		@Override
		protected EndPoint newEndPoint(SocketChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException {
			return new SelectChannelEndPoint(channel, selector, selectionKey, getScheduler(), 0);
		}

		@Override
		public Connection newConnection(SocketChannel channel, EndPoint endpoint, Object attachment) throws IOException {
			final Map<String, Object> context = (Map<String, Object>) attachment;
			HttpDestination httpDestination = (HttpDestination) context.get(HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY);
			context.put(SslClientConnectionFactory.SSL_PEER_HOST_CONTEXT_KEY, httpDestination.getHost());
			context.put(SslClientConnectionFactory.SSL_PEER_PORT_CONTEXT_KEY, httpDestination.getPort());
			ClientConnectionFactory ccf = httpDestination.getClientConnectionFactory();
			return ccf.newConnection(endpoint, context);
		}
	}
}
