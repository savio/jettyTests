package testjettys2;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.http2.HTTP2Session;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.HTTP2ClientConnectionFactory;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.client.http.HttpConnectionOverHTTP2;
import org.eclipse.jetty.http2.client.http.HttpDestinationOverHTTP2;
import org.eclipse.jetty.http2.frames.GoAwayFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.util.Promise;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.jetty.client.HttpClientTransport.HTTP_CONNECTION_PROMISE_CONTEXT_KEY;
import static org.eclipse.jetty.client.HttpClientTransport.HTTP_DESTINATION_CONTEXT_KEY;

/**
 * Created by smartin on 21/03/16.
 */
public class Http2InitiatorHelper {
	private MyHttpClientTransportOverHTTP2 http2Transport;
	private HTTP2Client http2Client;

	static public class  MyHttpClientTransportOverHTTP2 extends HttpClientTransportOverHTTP2{

		public MyHttpClientTransportOverHTTP2(HTTP2Client client) {
			super(client);
		}
		@Override
		public void onClose(HttpConnectionOverHTTP2 connection, GoAwayFrame frame) {
			super.onClose(connection, frame);
		}
	}

	public Http2InitiatorHelper() {
		http2Client = new HTTP2Client();
		http2Transport = new MyHttpClientTransportOverHTTP2(http2Client);
		http2Transport.setUseALPN(false);
	}


	public void setHttpClient(HttpClient httpClient) {
		http2Transport.setHttpClient(httpClient);
		http2Client.setConnectTimeout(httpClient.getConnectTimeout());
		httpClient.addBean(http2Transport);
	}
	public void start() throws Exception {
		//
		http2Transport.start();
		http2Client.start();
	}

	public HttpClientTransportOverHTTP2 getHttp2Transport() {
		return http2Transport;
	}



	public void buildContext( Map<String, Object> context,HttpDestinationOverHTTP2 destination){
		@SuppressWarnings("unchecked")
		Promise<Session> listenerPromise = new SessionListenerPromise(context);

		context.put(HTTP2ClientConnectionFactory.CLIENT_CONTEXT_KEY, http2Client);
		context.put(HTTP2ClientConnectionFactory.SESSION_LISTENER_CONTEXT_KEY, listenerPromise);
		context.put(HTTP2ClientConnectionFactory.SESSION_PROMISE_CONTEXT_KEY, listenerPromise);

		context.put(HTTP2ClientConnectionFactory.BYTE_BUFFER_POOL_CONTEXT_KEY, http2Client.getByteBufferPool());
		context.put(HTTP2ClientConnectionFactory.EXECUTOR_CONTEXT_KEY, http2Client.getExecutor());
		context.put(HTTP2ClientConnectionFactory.SCHEDULER_CONTEXT_KEY, http2Client.getScheduler());
		context.put(HTTP_CONNECTION_PROMISE_CONTEXT_KEY,destination);


	}



	static public class  MyHttpDestinationOverHTTP2 extends HttpDestinationOverHTTP2 {
		boolean first=true;
		public MyHttpDestinationOverHTTP2(HttpClient client, Origin origin) {
			super(client, origin);
		}

		@Override
		public void newConnection(Promise<Connection> promise) {
			/**
			 * We provide the connection at the first time
			 */
			if(first){
				first=false;
			}else{
				super.newConnection(promise);
			}
		}

		/**
		 * Hack to be public
		 * @param connection
		 * @return
		 */
		@Override
		public boolean process(HttpConnectionOverHTTP2 connection) {
			return super.process(connection);
		}
	}

	/**
	 * Copy past because it's private on original and it uses some fields of original
	 */

	private class SessionListenerPromise extends Session.Listener.Adapter implements Promise<Session>
	{
		private final Map<String, Object> context;
		private HttpConnectionOverHTTP2 connection;

		private SessionListenerPromise(Map<String, Object> context)
		{
			this.context = context;
		}

		@Override
		public void succeeded(Session session)
		{
			connection =  new HttpConnectionOverHTTP2(destination(), session);
			promise().succeeded(connection);
		}

		@Override
		public void failed(Throwable failure)
		{
			promise().failed(failure);
		}

		private HttpDestinationOverHTTP2 destination()
		{
			return (HttpDestinationOverHTTP2)context.get(HTTP_DESTINATION_CONTEXT_KEY);
		}

		@SuppressWarnings("unchecked")
		private Promise<Connection> promise()
		{
			return (Promise<Connection>)context.get(HTTP_CONNECTION_PROMISE_CONTEXT_KEY);
		}

		@Override
		public Map<Integer, Integer> onPreface(Session session)
		{
			Map<Integer, Integer> settings = new HashMap<>();
			settings.put(SettingsFrame.INITIAL_WINDOW_SIZE, http2Client.getInitialStreamRecvWindow());
			return settings;
		}

		@Override
		public void onSettings(Session session, SettingsFrame frame)
		{
			Map<Integer, Integer> settings = frame.getSettings();
			if (settings.containsKey(SettingsFrame.MAX_CONCURRENT_STREAMS))
				destination().setMaxRequestsPerConnection(settings.get(SettingsFrame.MAX_CONCURRENT_STREAMS));
		}

		@Override
		public void onClose(Session session, GoAwayFrame frame)
		{
			http2Transport.onClose(connection, frame);
		}

		@Override
		public boolean onIdleTimeout(Session session)
		{
			return connection.onIdleTimeout(((HTTP2Session)session).getEndPoint().getIdleTimeout());
		}

		@Override
		public void onFailure(Session session, Throwable failure)
		{
			HttpConnectionOverHTTP2 c = connection;
			if (c != null)
				c.close();
		}
	}
}
