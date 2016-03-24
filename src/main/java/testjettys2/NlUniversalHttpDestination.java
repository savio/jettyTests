package testjettys2;

import org.eclipse.jetty.client.*;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.client.http.HttpDestinationOverHTTP;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.http.HttpConnectionOverHTTP2;
import org.eclipse.jetty.http2.client.http.HttpDestinationOverHTTP2;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.util.Promise;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.eclipse.jetty.client.HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY;

/**
 * Created by smartin on 18/03/16.
 */
public class NlUniversalHttpDestination extends HttpDestination {
	private final Http2InitiatorHelper http2InitiatorHelper;
	private List<Response.ResponseListener> listeners;

	enum ProtocolState {UNKNOWN, HTTP1, HTTP2}

	;
	ProtocolState protocolState = ProtocolState.UNKNOWN;
	final HttpClient httpClient;
	public static final List<String> protocols = Arrays.asList("http/1.1", "h2", "h2-14", "h2-15", "h2-16", "h2-17");
	public static final String default_protocol = "http/1.1";

	private final HttpClientTransportOverHTTP http1Transport;
	private final HttpClientTransport transport;
	protected ClientConnectionFactory clientConnectionFactory;
	private HttpRequest request = null;

	HttpDestination selectedHttpDestination = null;
	HttpClientTransport selectedHttpClientTransport = null;




	public NlUniversalHttpDestination(HttpClient client, Origin origin, HttpClientTransport transport, HttpClientTransportOverHTTP http1Transport, Http2InitiatorHelper http2InitiatorHelper) {
		super(client, origin);
		this.httpClient = client;

		this.http1Transport = http1Transport;
		//this.http2Transport = http2InitiatorHelper.getHttp2Transport();
		this.http2InitiatorHelper = http2InitiatorHelper;
		this.transport = transport;




		ClientConnectionFactory ccf = new UniversalConnectionFactory(/*httpClient.getExecutor(),httpClient*/);

		//This is ssl only for the moment. In the future we can disable ssl connector in case.
		ccf = new NlALPNClientConnectionFactory(httpClient.getExecutor(), ccf, protocols, default_protocol);
		if (this.getProxy() == null) {
			ccf = new SslClientConnectionFactory(httpClient.getSslContextFactory(), httpClient.getByteBufferPool(), httpClient.getExecutor(), ccf);
		} else {

			//new HttpProxy()
			//ccf = httpProxy.newClientConnectionFactory(ccf);//TODO
		}
		clientConnectionFactory = ccf;
	}


	@Override
	public ClientConnectionFactory getClientConnectionFactory() {
		return clientConnectionFactory;
	}


	@Override
	protected void send(HttpRequest request, List<Response.ResponseListener> listeners) {
		this.request = request;
		this.listeners = listeners;
		super.send(request, listeners);
	}

	@Override
	public void send() {
		switch (protocolState) {
			case UNKNOWN://TODO call directly the good destination on negotiated.
				newConnection(new Promise<org.eclipse.jetty.client.api.Connection>() {
					@Override
					public void succeeded(org.eclipse.jetty.client.api.Connection result) {

					}

					@Override
					public void failed(Throwable x) {
						System.out.println("failed");
						x.printStackTrace();
					}
				});
		}

	}

	private class UniversalConnectionFactory implements ClientConnectionFactory {

		/**
		 * See the response and create the destination depend of ALPN
		 * @param endPoint
		 * @param context
		 * @return
		 * @throws IOException
		 */
		@Override
		public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException {
			String protocol = (String) context.get(NlALPNClientConnectionFactory.ALPN_NEGOTIATED_PROTOCOL);
			NlUniversalHttpDestination uDest = (NlUniversalHttpDestination) context.get(HTTP_DESTINATION_CONTEXT_KEY);
			if ("http/1.1".equals(protocol)) {
				protocolState = ProtocolState.HTTP1;
				request.version(HttpVersion.HTTP_1_1);
				selectedHttpClientTransport = http1Transport;
				//selectedHttpDestination = selectedHttpClientTransport.newHttpDestination(uDest.getOrigin(), uDest.getProxyOrigin());
				selectedHttpDestination = new HttpDestinationOverHTTP(getHttpClient(), uDest.getOrigin());
				selectedHttpDestination.getHttpExchanges().addAll(NlUniversalHttpDestination.this.getHttpExchanges());
				//((MyHttpDestinationOverHTTP)selectedHttpDestination).send(request,listeners);
				//((MyHttpDestinationOverHTTP)selectedHttpDestination).process(new  HttpConnectionOverHTTP(endPoint,selectedHttpDestination,promiseBlockingObject.get()));

				//context.put(HTTP_CONNECTION_PROMISE_CONTEXT_KEY,promiseBlockingObject.get());

				context.put(HTTP_DESTINATION_CONTEXT_KEY, selectedHttpDestination); //change the context
				context.remove(SslClientConnectionFactory.SSL_CONTEXT_FACTORY_CONTEXT_KEY);//Avoid ALPN twice
				//Connection connection =  selectedHttpClientTransport.newConnection(endPoint, context);
				HttpConnectionOverHTTP connection = (HttpConnectionOverHTTP)selectedHttpClientTransport.newConnection(endPoint, context);
				((HttpDestinationOverHTTP)selectedHttpDestination).process(connection);
				//Connection connection = new  HttpConnectionOverHTTP(endPoint,selectedHttpDestination,promiseBlockingObject.get());

				return connection;

				//hd.send();
				//((HttpDestinationOverHTTP)hd).getConnectionPool().getIdleConnections().add()
				//context.put(HttpClientTransportOverHTTP.HTTP_CONNECTION_PROMISE_CONTEXT_KEY,hd);
			} else {

				protocolState = ProtocolState.HTTP2;
				request.version(HttpVersion.HTTP_2);

				selectedHttpClientTransport = http2InitiatorHelper.getHttp2Transport();
				//selectedHttpDestination = selectedHttpClientTransport.newHttpDestination(uDest.getOrigin(), uDest.getProxyOrigin());
				selectedHttpDestination = newHttp2Destination(uDest.getOrigin());
				selectedHttpDestination.getHttpExchanges().addAll(NlUniversalHttpDestination.this.getHttpExchanges()); //try to avoid call twice

				http2InitiatorHelper.buildContext(context, (HttpDestinationOverHTTP2) selectedHttpDestination);
				context.put(HTTP_DESTINATION_CONTEXT_KEY, selectedHttpDestination); //change the context

				context.remove(SslClientConnectionFactory.SSL_CONTEXT_FACTORY_CONTEXT_KEY);//Avoid ALPN twice

				Connection connection = selectedHttpClientTransport.newConnection(endPoint, context);
				selectedHttpDestination.send();
				//((Http2InitiatorHelper.MyHttpDestinationOverHTTP2)selectedHttpDestination).process((HttpConnectionOverHTTP2)connection);
				//((HttpDestinationOverHTTP2)selectedHttpDestination).process(connection);
				return connection;
			}
		}
	}
	public HttpDestination newHttp2Destination(Origin origin) {
		return new Http2InitiatorHelper.MyHttpDestinationOverHTTP2(httpClient,origin);
	}
}
