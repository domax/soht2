/* SOHT2 © Licensed under MIT 2026. */
package net.soht2.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.val;
import net.soht2.client.service.HCClientHttpRequestFactory;
import org.apache.http.HttpHost;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HttpConnectionPoolTest {

  private static Soht2ClientProperties properties(int maxHttpConnections) {
    val props = new Soht2ClientProperties();
    props.setMaxHttpConnections(maxHttpConnections);
    return props;
  }

  @Test
  void trustingClientConnectionManager_appliesMaxHttpConnections() {
    val cm =
        (PoolingHttpClientConnectionManager)
            new TrustingSSLConfig().trustingClientConnectionManager(properties(4));
    assertThat(cm.getMaxTotal()).isEqualTo(4);
    assertThat(cm.getDefaultMaxPerRoute()).isEqualTo(4);
  }

  @Test
  void proxyClientHttpRequestFactory_appliesMaxHttpConnections() {
    val factory =
        (HCClientHttpRequestFactory)
            new ProxyConfig()
                .clientHttpRequestFactory(
                    properties(7), new HttpHost("proxy", 3128), null, null, null);
    val cm =
        (PoolingHttpClientConnectionManager)
            ReflectionTestUtils.getField(factory.getHttpClient(), "connManager");
    assertThat(cm).isNotNull();
    assertThat(cm.getMaxTotal()).isEqualTo(7);
    assertThat(cm.getDefaultMaxPerRoute()).isEqualTo(7);
  }
}
