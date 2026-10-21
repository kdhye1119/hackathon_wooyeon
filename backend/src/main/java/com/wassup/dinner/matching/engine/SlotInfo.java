package com.wassup.dinner.matching.engine;

import java.time.LocalDate;

public record SlotInfo(long id, LocalDate date, String region, String label) {
}
