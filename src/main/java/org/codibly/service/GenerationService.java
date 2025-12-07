package org.codibly.service;

import org.codibly.dto.response.GenerationResponse;
import org.codibly.time.TimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class GenerationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final TimeProvider timeProvider;

    private static final DateTimeFormatter API_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");

    public GenerationService(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public GenerationResponse getGenerationResponse() {

        OffsetDateTime startUtc = timeProvider.get().toOffsetDateTime();
        OffsetDateTime endUtc = startUtc.plusDays(3);

        String fromStr = startUtc.format(API_FORMATTER);
        String toStr = endUtc.format(API_FORMATTER);

        String url = "https://api.carbonintensity.org.uk/generation/" + fromStr + "/" + toStr;

        return restTemplate.getForObject(url, GenerationResponse.class);
    }
}
