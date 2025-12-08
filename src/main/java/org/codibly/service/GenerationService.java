package org.codibly.service;

import org.codibly.dto.response.DailyGenerationResponse;
import org.codibly.dto.response.GenerationResponse;
import org.codibly.dto.response.GenerationResponse.GenerationEntry;
import org.codibly.dto.response.OptimalChargingWindowResponse;
import org.codibly.exception.GenerationProviderConnectionException;
import org.codibly.exception.NoGenerationFoundExcepion;
import org.codibly.model.EnergySource;
import org.codibly.time.TimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GenerationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final TimeProvider timeProvider;

    private static final DateTimeFormatter API_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");

    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public GenerationService(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    /**
     * Fetches predicted electricity generation data for three days
     * and calculates the average share of each energy source
     * and the total clean energy percentage.
     *
     * @return a list of DTOs containing the daily averages
     */
    public List<DailyGenerationResponse> getThreeDaysAverage() {
        GenerationResponse response = fetchGenerationData(3);
        Map<String, List<GenerationEntry>> grouped = groupEntriesByDate(response);

        return calculateDailyAverages(grouped);
    }

    /**
     * Fetches generation data from the Carbon Intensity API for a given number of days.
     *
     * @param days number of days to fetch forecast data for
     * @return API response containing a list of generation entries
     * @throws NoGenerationFoundExcepion if no data is found in the API response
     * @throws GenerationProviderConnectionException if there is a connection issue with the API
     */
    //TODO przeniesc to do CarbonIntensityClient
    private GenerationResponse fetchGenerationData(int days) {
        OffsetDateTime startUtc = timeProvider.get().toOffsetDateTime();
        OffsetDateTime endUtc = startUtc.plusDays(days);

        String url = "https://api.carbonintensity.org.uk/generation/"
                + startUtc.format(API_FORMATTER) + "/" + endUtc.format(API_FORMATTER);

        try {
            return Optional.ofNullable(restTemplate.getForObject(url, GenerationResponse.class))
                    .orElseThrow(() -> new NoGenerationFoundExcepion("No generation data found for the requested period."));
        } catch (RestClientException ex) {
            throw new GenerationProviderConnectionException("Failed to fetch data from CarbonIntensity API", ex);
        }
    }

    /**
     * Groups generation entries by date.
     *
     * @param response API response containing generation entries
     * @return a map where the key is the day (yyyy-MM-dd) and the value is the list of entries for that day
     */
    private Map<String, List<GenerationEntry>> groupEntriesByDate(GenerationResponse response) {
        return response.data().stream()
                .collect(Collectors.groupingBy(e -> e.from().format(DAY_FORMATTER)));
    }

    /**
     * Calculates daily averages for the grouped generation entries,
     * including the average share of each energy source and the total clean energy percentage.
     *
     * @param grouped map of entries grouped by date
     * @return list of DTOs containing daily averages
     */
    private List<DailyGenerationResponse> calculateDailyAverages(Map<String, List<GenerationEntry>> grouped) {
        return grouped.entrySet().stream()
                //TODO: zastanowić sie nam mappowaniem w package mapper
                .map(entry -> {
                    String day = entry.getKey();
                    Map<String, Double> avgMix = calculateAverageMix(entry.getValue());
                    double cleanPerc = avgMix.values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    return new DailyGenerationResponse(day, avgMix, cleanPerc);
                })
                .sorted(Comparator.comparing(DailyGenerationResponse::date))
                .toList();
    }

    /**
     * Calculates the average share of clean energy sources (biomass, nuclear, hydro, wind, solar)
     * for a list of generation entries.
     *
     * @param entries list of generation entries
     * @return a map where the key is the energy source and the value is the average percentage
     */
    private Map<String, Double> calculateAverageMix(List<GenerationEntry> entries) {
        int count = entries.size();

        return entries.stream()
                .flatMap(this::cleanEnergyStream)
                .collect(Collectors.groupingBy(
                        GenerationEntry.FuelMix::fuel,
                        Collectors.summingDouble(GenerationEntry.FuelMix::perc)
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() / count
                ));
    }

    /**
     * Finds the optimal charging window for an electric vehicle based on the highest
     * average clean energy share for a given window length in hours.
     *
     * @param hours charging window length in full hours (1-6)
     * @return DTO containing start time, end time, and average clean energy percentage
     * @throws IllegalArgumentException if the window length is outside the range 1-6
     * @throws NoGenerationFoundExcepion if there is not enough data to calculate the window
     */
    public OptimalChargingWindowResponse findOptimalChargingWindow(int hours) {
        if (hours < 1 || hours > 6) {
            throw new IllegalArgumentException("Charging window length must be between 1 and 6 hours");
        }

        GenerationResponse response = fetchGenerationData(2);
        List<GenerationEntry> entries = response.data();

        int windowSize = hours * 2;
        if (entries.size() < windowSize) {
            throw new NoGenerationFoundExcepion("Not enough data to calculate the optimal window");
        }

        double maxAverage = -1;
        int bestStartIndex = 0;

        for (int i = 0; i <= entries.size() - windowSize; i++) {
            List<GenerationEntry> windowEntries = entries.subList(i, i + windowSize);
            double avgClean = windowEntries.stream()
                    .mapToDouble(entry -> cleanEnergyStream(entry)
                            .mapToDouble(GenerationEntry.FuelMix::perc)
                            .sum())
                    .average()
                    .orElse(0.0);

            if (avgClean > maxAverage) {
                maxAverage = avgClean;
                bestStartIndex = i;
            }
        }

        GenerationEntry startEntry = entries.get(bestStartIndex);
        GenerationEntry endEntry = entries.get(bestStartIndex + windowSize - 1);

        return new OptimalChargingWindowResponse(
                startEntry.from().toOffsetDateTime(), // TODO: ujednolicić typ daty
                endEntry.to().toOffsetDateTime(),
                maxAverage
        );
    }

    private Stream<GenerationEntry.FuelMix> cleanEnergyStream(GenerationEntry entry) {
        return entry.generationmix().stream()
                .filter(f -> Arrays.stream(EnergySource.values())
                        .anyMatch(es -> es.getFuelName().equals(f.fuel())));
    }
}
