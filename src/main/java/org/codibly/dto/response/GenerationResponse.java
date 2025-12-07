package org.codibly.dto.response;

import java.time.ZonedDateTime;
import java.util.List;

public record GenerationResponse(
        List<GenerationEntry> data
) {
    public record GenerationEntry(
            ZonedDateTime from,
            ZonedDateTime to,
            List<FuelMix> generationmix
    ) {
        public record FuelMix(
                String fuel,
                double perc
        ) {}
    }
}