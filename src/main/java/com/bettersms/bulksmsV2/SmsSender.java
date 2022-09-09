package com.bettersms.bulksmsV2;

import com.bettersms.bulksmsV2.model.BulkOutbox;
import com.bettersms.bulksmsV2.service.OutboxMessageService;
import com.bettersms.bulksmsV2.service.TokenCacheService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rabbitmq.client.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Component
@Slf4j
@Data
public class SmsSender implements ChannelAwareMessageListener {

    private final String SMS_ENDPOINT = "/api/public/CMS/bulksms";

    @Value("${safemode.safendpoint}")
    private String safariBulkSmsEndpoint;

    private RestTemplate restTemplate;

    private TaskExecutor executor;

    private OutboxMessageService msisdnRepository;

    private RetryTemplate retryTemplate;

    private TokenCacheService cacheService;

    private Environment environment;

    public SmsSender(@Qualifier("consumer-workers") TaskExecutor executor, OutboxMessageService msisdnRepository, RetryTemplate retryTemplate, TokenCacheService cacheService, Environment environment) {
        this.executor = executor;
        this.msisdnRepository = msisdnRepository;
        this.retryTemplate = retryTemplate;
        this.cacheService = cacheService;
        this.environment = environment;
    }

    @PostConstruct
    protected void init() {
        restTemplate = new RestTemplate(); //you can configure timeouts etc if you want. but defaults are fine for now
    }

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long start = System.nanoTime();

        try {

            String accessToken = cacheService.getAccessToken("BulkAPI");
            String senderName = environment.getProperty("sendsms.userName");


            String msg = new String(message.getBody());
            JsonObject jsonMessageObject = getJsonMessageObject(msg);
            String kmpRecipients = jsonMessageObject.get("kmpRecipients").getAsString();
            String kmpMessage = jsonMessageObject.get("kmpMessage").getAsString();
            String responseDlrEndpoint = jsonMessageObject.get("resultUrl").getAsString();
            String kmpCorrelator = jsonMessageObject.get("kmpCorrelator").getAsString();
            String oa = jsonMessageObject.get("senderId").getAsString();


            Map<String, String> dataSet = getStringStringMap(oa,
                    senderName,
                    responseDlrEndpoint,
                    kmpRecipients,
                    kmpMessage,
                    kmpCorrelator);

            Map<String, Object> envelop = new HashMap<>();
            envelop.put("timeStamp", String.valueOf(System.currentTimeMillis()));
            envelop.put("dataSet", new ArrayList<Object>(Arrays.asList(dataSet)));
            BulkOutbox bulkOutbox = getBulkOutboxObject(kmpRecipients, kmpMessage, kmpCorrelator, oa, responseDlrEndpoint);

            msisdnRepository.saveSentMessage(bulkOutbox);

            terminateMessage(envelop, accessToken);
        } catch (Exception ex) {
            log.error("Failed to terminate message. {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            log.info("Request turn around time: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
        }
    }

    private BulkOutbox getBulkOutboxObject(String kmpRecipients, String kmpMessage, String kmpCorrelator, String oa, String resultDlrEndpoint) {
        BulkOutbox bulkOutbox = BulkOutbox.builder()
                .senderId(oa)
                .msisdn(kmpRecipients)
                .message(kmpMessage)
                .correlator(kmpCorrelator)
                .resultUrl(resultDlrEndpoint)
                .createdAt(LocalDateTime.now())
                .build();
        return bulkOutbox;
    }

    private JsonObject getJsonMessageObject(String msg) {
        JsonObject jsonMessageObject = new Gson().fromJson(msg, JsonObject.class);
        return jsonMessageObject;
    }

    private Map<String, String> getStringStringMap(String oa, String senderName, String actionResponseURL, String kmp_recipients, String kmp_message, String kmp_correlator) {
        Map<String, String> dataSet = new HashMap<>();
        dataSet.put("userName", senderName);
        dataSet.put("oa", oa);
        dataSet.put("msisdn", kmp_recipients);
        dataSet.put("channel", "sms");
        dataSet.put("uniqueId", kmp_correlator);
        dataSet.put("message", kmp_message);
        dataSet.put("actionResponseURL", actionResponseURL);
        return dataSet;
    }

    @SuppressWarnings({"unchecked", "deprecated"})
    protected void terminateMessage(Map<String, Object> payload, String accessToken) {
        retryTemplate.execute(((retryContext) -> {
            RequestEntity<Map<String, Object>> request = RequestEntity.post(
                            URI.create(environment.getProperty(safariBulkSmsEndpoint,
                                    "https://dsvc.safaricom.com:9480").concat(SMS_ENDPOINT)))
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Authorization", String.format("Bearer %s", accessToken))
                    .body(payload);

            ResponseEntity<Map> responseEntity = restTemplate.exchange(request, Map.class);
            String mapAsString = payload.keySet().stream().map(key -> key + "=" + payload.get(key)).collect(Collectors.joining(", ", "{", "}"));
            log.info("REQUEST <<<< && RESPONSE >>>>>>>>>>>>\n");

            log.info("Request body: {}", mapAsString);
            log.info(">>>>> <<<<<<\n");
            log.debug("Send SMS response: {}. {}", responseEntity.getStatusCode(), responseEntity.getBody());

            if (HttpStatus.OK.equals(responseEntity.getStatusCode())
                    || HttpStatus.ACCEPTED.equals(responseEntity.getStatusCode())) {
                log.info("|<<<|>" + responseEntity.getBody() + "<|>>|");
                return mapAsString;
            } else {
                log.error("Failed to get terminate message. {}-{}. Will retry if max retries not reached. {} attempts", responseEntity.getStatusCode(),
                        responseEntity.getBody(), retryContext.getRetryCount());
                throw new RestClientException(String.format("Non-success response %s %s for request. ",
                        responseEntity.getStatusCode(), responseEntity.getBody()));
            }
        }));
    }
}