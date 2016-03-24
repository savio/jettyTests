package test;
import org.eclipse.jetty.alpn.ALPN;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;


/**
 * Created by smartin on 24/03/16.
 */
public class JettyClient {
	public static void main(String[] args) throws Exception {
		ALPN.debug = true;


		SslContextFactory sslContextFactory = new SslContextFactory();
		sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.1");
		//sslContextFactory.addExcludeCipherSuites(".*CBC.*");
		sslContextFactory.setTrustAll(true);
		sslContextFactory.setExcludeCipherSuites(
				"SSL_RSA_WITH_DES_CBC_SHA",
				"SSL_DHE_RSA_WITH_DES_CBC_SHA",
				"SSL_DHE_DSS_WITH_DES_CBC_SHA",
				"SSL_RSA_EXPORT_WITH_RC4_40_MD5",
				"SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
				"SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
				"SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
		//client.addBean(sslContextFactory);
		System.out.println("-------------");
		ALPN.debug=true;
	HTTP2Client lowLevelClient = new HTTP2Client();


		// create a high-level Jetty client
		HttpClient client = new HttpClient(new HttpClientTransportOverHTTP2(lowLevelClient), sslContextFactory);
		lowLevelClient.start();
		client.start();

		HttpProxy httpProxy = new HttpProxy("localhost", 8888);
		ProxyConfiguration proxyConfig = client.getProxyConfiguration();
		proxyConfig.getProxies().add(httpProxy);
		// request-response exchange
		Request request = client.newRequest("https://www.youtube.com/")
				.method(HttpMethod.GET)
				.accept("text/html")
				.agent("neoneo");



		ContentResponse response = request.send(/*new Origin("http","localhost",8888)*/);
		//ContentResponse response = client.GET("http://www.youtube.com");
		//ContentResponse response = client.newRequest("https://www.youtube.com/").send();
		System.out.println("Version: " + response.getVersion());
		System.out.println("Status: " + response.getStatus());
		System.out.println(response.getReason());
		System.out.println(response.getHeaders());
		//Thread.sleep(100000);
		client.stop();
	}
}
