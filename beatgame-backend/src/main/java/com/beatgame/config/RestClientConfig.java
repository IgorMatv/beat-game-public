package com.beatgame.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class RestClientConfig {

    @Bean("deezerClient")
    public RestClient deezerClient(@Value("${deezer.api.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("itunesClient")
    public RestClient itunesClient(@Value("${itunes.api.base-url}") String baseUrl) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(new ObjectMapper());
        converter.setSupportedMediaTypes(List.of(
            MediaType.APPLICATION_JSON,
            MediaType.valueOf("text/javascript")
        ));
        return RestClient.builder()
            .baseUrl(baseUrl)
            .messageConverters(converters -> {
                converters.clear();
                converters.add(converter);
            })
            .build();
    }
}
