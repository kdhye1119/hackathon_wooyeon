package com.wassup.dinner.matching.engine;

import java.util.List;
import java.util.Map;

public record MatchResult(
        List<PlannedTable> tables,
        List<Unplaced> unplaced,
        /* 신청 ID → 자동 재배치 횟수 */
        Map<Long, Integer> reallocationCount,
        /* 신청 ID → 최종 배치 사유 */
        Map<Long, String> placementReason,
        Stats stats,
        List<String> log) {

    public record PlannedTable(long slotId, List<Candidate> members, ScoreBreakdown score, String formation) {
    }

    public record Unplaced(Candidate candidate, String reason) {
    }

    public record Stats(int candidates, int initialTables, int splits, int merges, int reallocated,
                        int dissolved, int confirmedTables, int unplaced) {
    }
}
