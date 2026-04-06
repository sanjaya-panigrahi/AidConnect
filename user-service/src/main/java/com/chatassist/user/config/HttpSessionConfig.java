package com.chatassist.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 300, redisNamespace = "chatassist:session")
public class HttpSessionConfig {

	@Bean
	CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("SESSION");
		serializer.setUseBase64Encoding(false);
		serializer.setSameSite("Lax");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setCookiePath("/");
		return serializer;
	}
}

