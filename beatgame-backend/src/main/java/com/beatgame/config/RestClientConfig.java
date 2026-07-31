package com.beatgame.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Configuration
public class RestClientConfig {

    @Bean("deezerClient")
    public RestClient deezerClient(@Value("${deezer.api.base-url}") String baseUrl,
                                    @Value("${deezer.api.connect-timeout-ms}") long connectTimeoutMs,
                                    @Value("${deezer.api.read-timeout-ms}") long readTimeoutMs) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
            .build();
    }

    @Bean("itunesClient")
    public RestClient itunesClient(@Value("${itunes.api.base-url}") String baseUrl,
                                    @Value("${itunes.api.connect-timeout-ms}") long connectTimeoutMs,
                                    @Value("${itunes.api.read-timeout-ms}") long readTimeoutMs) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(new ObjectMapper());
        converter.setSupportedMediaTypes(List.of(
            MediaType.APPLICATION_JSON,
            MediaType.valueOf("text/javascript")
        ));
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
            .messageConverters(converters -> {
                converters.clear();
                converters.add(converter);
            })
            .build();
    }

    private ClientHttpRequestFactory requestFactory(long connectTimeoutMs, long readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        return ClientHttpRequestFactories.get(settings);
    }
}
