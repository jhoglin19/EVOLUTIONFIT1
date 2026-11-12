package com.evolutionfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/** En producción solo Caddy (red interna 172.28.10.0/24 del compose) puede indicar la IP del cliente. */
class ProdTrustedProxyConfigTest {

    @Test
    void onlyTheComposeInternalNetworkIsTrusted() throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        for (String file : new String[] {"application.yml", "application-prod.yml"}) {
            new YamlPropertySourceLoader().load(file, new ClassPathResource(file)).forEach(sources::addFirst);
        }
        var env = new PropertySourcesPropertyResolver(sources);

        assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        Pattern trusted = Pattern.compile(env.getProperty("server.tomcat.remoteip.internal-proxies"));
        assertThat(trusted.matcher("172.28.10.4").matches()).isTrue();
        // La puerta de enlace de Docker, otras redes privadas, localhost e internet no son de confianza.
        assertThat(trusted.matcher("172.28.11.4").matches()).isFalse();
        assertThat(trusted.matcher("192.168.65.1").matches()).isFalse();
        assertThat(trusted.matcher("10.0.0.1").matches()).isFalse();
        assertThat(trusted.matcher("127.0.0.1").matches()).isFalse();
        assertThat(trusted.matcher("203.0.113.10").matches()).isFalse();
    }
}
