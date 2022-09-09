package com.bettersms.bulksmsV2.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TokenCacheService {
    protected static final String TOKEN_GENERATE_ENDPOINT = "/api/auth/login";

    @Value("${safemode.safendpoint}")
    private String safendpoint;
    private Environment environment;
    private RetryTemplate retryTemplate;
    private RestTemplate restClient;
    private ObjectMapper mapper;
    private LoadingCache<String, String> tokenCache;

    public TokenCacheService(Environment environment, RetryTemplate retryTemplate) {
        this.environment = environment;
        this.retryTemplate = retryTemplate;
    }

    @PostConstruct
    protected void init() {
        mapper = new ObjectMapper();
        restClient = new RestTemplate();
        tokenCache = CacheBuilder.newBuilder().expireAfterWrite(environment.getProperty("sixd.cache.ttl", Integer.class, 25),
                TimeUnit.MINUTES).build(new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                log.info("Loading cache with token for username {}", key);
                return String.valueOf(fetchToken(key).get("token"));
            }
        });
    }

    /**
     * Invokes with API username of provider.
     *
     * @param userName username
     * @return token
     * @throws ExecutionException
     */
    public String getAccessToken(String userName) throws ExecutionException {
        return tokenCache.get(userName);
    }

    private Map<String, Object> fetchToken(String username) {
        return retryTemplate.execute((rc) -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("username", environment.getProperty("spring.rabbitmq.apiuser"));
                params.put("password", environment.getProperty("spring.rabbitmq.apipassword"));

                log.info("Refreshing token cache for provider {}...", username);

                RequestEntity<String> request = RequestEntity.post(URI.create(environment
                                .getProperty("sixd.server.api.host", safendpoint)
                                .concat(TOKEN_GENERATE_ENDPOINT)))
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .body(mapper.writeValueAsString(params));

                log.debug("Request body: {}", params);

                ResponseEntity<Map> responseEntity = restClient.exchange(request, Map.class);

                log.debug("Token generate response: {}. {}", responseEntity.getStatusCode(), responseEntity.getBody());
                if (HttpStatus.OK.equals(responseEntity.getStatusCode())
                        || HttpStatus.ACCEPTED.equals(responseEntity.getStatusCode())) {
                    return responseEntity.getBody();
                } else {
                    log.error("Failed to get access token. {}. Will retry", responseEntity.getStatusCode(), responseEntity.getBody());
                    throw new RestClientException(String.format("Non-success response %s %s for token request. ", responseEntity.getStatusCode(), responseEntity.getBody()));
                }
            } catch (RestClientException | JsonProcessingException e) {
                log.error("Error Fetching authentication token: {}. {} attempts", e.getMessage(), rc.getRetryCount(), e);
                throw new RestClientException(e.getMessage(), e);
            }
        });
    }

}