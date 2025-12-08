package org.codibly.service;

import org.codibly.dto.response.DailyGenerationResponse;
import org.codibly.dto.response.GenerationResponse;
import org.codibly.dto.response.GenerationResponse.GenerationEntry;
import org.codibly.dto.response.GenerationResponse.GenerationEntry.FuelMix;
import org.codibly.dto.response.OptimalChargingWindowResponse;
import org.codibly.exception.GenerationProviderConnectionException;
import org.codibly.exception.NoGenerationFoundExcepion;
import org.codibly.model.EnergySource;
import org.codibly.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock
    private TimeProvider timeProvider;

    @Mock
    private RestTemplate restTemplateMock;

    @InjectMocks
    private GenerationService generationService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(generationService, "restTemplate", restTemplateMock);
    }

    @Test
    @DisplayName("Should find optimal charging window for 1-hour window")
    void findOptimalChargingWindow_For_1HourWindow() {
        // given
        GenerationResponse mockResponse = mockGenerationResponse();

        when(restTemplateMock.getForObject(anyString(), eq(GenerationResponse.class)))
                .thenReturn(mockResponse);
        when(timeProvider.get())
                .thenReturn(ZonedDateTime.parse("2025-01-01T00:00Z"));

        // when
        OptimalChargingWindowResponse result = generationService.findOptimalChargingWindow(1);

        // then
        GenerationEntry bestStart = mockResponse.data().get(2);
        GenerationEntry bestEnd = mockResponse.data().get(3);

        assertThat(result.start()).isEqualTo(bestStart.from());
        assertThat(result.end()).isEqualTo(bestEnd.to());
        assertThat(result.averageCleanEnergyPercentage()).isEqualTo((80 + 90) / 2.0);

        verify(restTemplateMock).getForObject(anyString(), eq(GenerationResponse.class));
        verify(timeProvider).get();
    }

    @Test
    @DisplayName("Should find optimal charging window for 3-hour window")
    void findOptimalChargingWindow_For_3HourWindow() {
        // given
        GenerationResponse mockResponse = mockGenerationResponse();

        when(restTemplateMock.getForObject(anyString(), eq(GenerationResponse.class)))
                .thenReturn(mockResponse);
        when(timeProvider.get())
                .thenReturn(ZonedDateTime.parse("2025-01-01T00:00Z"));

        // when
        OptimalChargingWindowResponse result = generationService.findOptimalChargingWindow(3);

        // then
        GenerationEntry bestStart = mockResponse.data().get(0);
        GenerationEntry bestEnd = mockResponse.data().get(5);

        assertThat(result.start()).isEqualTo(bestStart.from());
        assertThat(result.end()).isEqualTo(bestEnd.to());
        assertThat(result.averageCleanEnergyPercentage()).isEqualTo(39.5);

        verify(restTemplateMock).getForObject(anyString(), eq(GenerationResponse.class));
        verify(timeProvider).get();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 999, -1})
    @DisplayName("Should throw IllegalArgumentException for invalid window lengths")
    void findOptimalChargingWindow_InvalidWindow_ShouldThrow_IllegalArgumentException(int invalidHoursWindow) {
        // when & then
        assertThatThrownBy(() -> generationService.findOptimalChargingWindow(invalidHoursWindow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Charging window length must be between 1 and 6 hours");
    }

    @Test
    @DisplayName("Should calculate average clean energy for the next 3 days")
    void getThreeDaysAverage_ForNext3Days() {
        // given
        GenerationResponse resp = mockThreeDaysResponse();

        when(restTemplateMock.getForObject(anyString(), eq(GenerationResponse.class)))
                .thenReturn(resp);

        when(timeProvider.get())
                .thenReturn(ZonedDateTime.parse("2025-01-01T01:22Z"));

        // when
        List<DailyGenerationResponse> result = generationService.getThreeDaysAverage();

        // then
        assertThat(result).hasSize(3);

        DailyGenerationResponse day1 = result.get(0);
        assertThat(day1.date()).isEqualTo("2025-01-01");
        assertThat(day1.cleanEnergyPerc()).isEqualTo(7);

        DailyGenerationResponse day2 = result.get(1);
        assertThat(day2.date()).isEqualTo("2025-01-02");
        assertThat(day2.cleanEnergyPerc()).isEqualTo(60);

        DailyGenerationResponse day3 = result.get(2);
        assertThat(day3.date()).isEqualTo("2025-01-03");
        assertThat(day3.cleanEnergyPerc()).isEqualTo(15);

        verify(restTemplateMock).getForObject(anyString(), eq(GenerationResponse.class));
        verify(timeProvider).get();
    }

    @Test
    @DisplayName("Should throw NoGenerationFoundExcepion when API returns no data")
    void fetchGenerationData_NoData_ShouldThrow_NoGenerationFoundExcepion() {
        // given
        when(restTemplateMock.getForObject(anyString(), eq(GenerationResponse.class)))
                .thenReturn(null);
        when(timeProvider.get())
                .thenReturn(ZonedDateTime.parse("2025-01-01T00:00Z"));

        // when & then
        assertThatThrownBy(() -> generationService.getThreeDaysAverage())
                .isInstanceOf(NoGenerationFoundExcepion.class)
                .hasMessageContaining("No generation data found for the requested period");
    }

    @Test
    @DisplayName("Should throw GenerationProviderConnectionException when API fails")
    void fetchGenerationData_ApiFails_ShouldThrow_GenerationProviderConnectionException() {
        // given
        when(restTemplateMock.getForObject(anyString(), eq(GenerationResponse.class)))
                .thenThrow(new RestClientException("API down"));
        when(timeProvider.get())
                .thenReturn(ZonedDateTime.parse("2025-01-01T00:00Z"));

        // when & then
        assertThatThrownBy(() -> generationService.getThreeDaysAverage())
                .isInstanceOf(GenerationProviderConnectionException.class)
                .hasMessageContaining("Failed to fetch data from CarbonIntensity API");
    }



    private GenerationResponse mockGenerationResponse() {
        GenerationEntry e1 = entry("2025-01-01T00:00Z", "2025-01-01T00:30Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 5.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 10.0,
                EnergySource.SOLAR, 0.0
        ));
        GenerationEntry e2 = entry("2025-01-01T00:30Z", "2025-01-01T01:00Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 10.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 20.0,
                EnergySource.SOLAR, 0.0
        ));
        // The best entries are e3 and e4 (highest total clean energy percentage in the window)
        GenerationEntry e3 = entry("2025-01-01T01:00Z", "2025-01-01T01:30Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 20.0,
                EnergySource.HYDRO, 10.0,
                EnergySource.WIND, 50.0,
                EnergySource.SOLAR, 0.0
        ));
        GenerationEntry e4 = entry("2025-01-01T01:30Z", "2025-01-01T02:00Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 30.0,
                EnergySource.HYDRO, 10.0,
                EnergySource.WIND, 50.0,
                EnergySource.SOLAR, 0.0
        ));
        GenerationEntry e5 = entry("2025-01-01T02:00Z", "2025-01-01T02:30Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 5.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 10.0,
                EnergySource.SOLAR, 0.0
        ));
        GenerationEntry e6 = entry("2025-01-01T02:30Z", "2025-01-01T03:00Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 2.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 5.0,
                EnergySource.SOLAR, 0.0
        ));

        return new GenerationResponse(List.of(e1, e2, e3, e4, e5, e6));
    }

    private GenerationResponse mockThreeDaysResponse() {
        GenerationEntry day1 = entry("2025-01-01T00:00Z", "2025-01-01T00:30Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 5.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 2.0,
                EnergySource.SOLAR, 0.0
        ));

        GenerationEntry day2 = entry("2025-01-02T00:00Z", "2025-01-02T00:30Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 10.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 50.0,
                EnergySource.SOLAR, 0.0
        ));

        GenerationEntry day3 = entry("2025-01-03T00:00Z", "2025-01-03T00:30Z", Map.of(
                EnergySource.BIOMASS, 0.0,
                EnergySource.NUCLEAR, 2.0,
                EnergySource.HYDRO, 0.0,
                EnergySource.WIND, 13.0,
                EnergySource.SOLAR, 0.0
        ));

        return new GenerationResponse(List.of(day1, day2, day3));
    }

    private GenerationEntry entry(String from, String to, Map<EnergySource, Double> energyShare) {
        return new GenerationEntry(
                ZonedDateTime.parse(from),
                ZonedDateTime.parse(to),
                energyShare.entrySet().stream()
                        .map(e -> new FuelMix(e.getKey().getFuelName(), e.getValue()))
                        .toList()
        );
    }

}
