package com.codetrack.backend.util;

import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical ordering for any view that ranks multiple students by
 * performance. Priority:
 *  1. Overall Score (highest first)
 *  2. Total Problems Solved (highest first)
 *  3. Roll Number (ascending)
 *
 * Students without a score sort last (nulls last), then by solved, then roll.
 * The same comparator is used by the leaderboard and every generated PDF
 * report so the ordering is stable across the application.
 */
public final class PerformanceSort {

    private PerformanceSort() {
    }

    public static int totalSolved(Performance p) {
        if (p == null) {
            return 0;
        }
        int total = 0;
        if (p.getLeetcodeSolved() != null) {
            total += p.getLeetcodeSolved();
        }
        if (p.getCodeforcesSolved() != null) {
            total += p.getCodeforcesSolved();
        }
        if (p.getCodechefSolved() != null) {
            total += p.getCodechefSolved();
        }
        return total;
    }

    public static Comparator<Performance> performanceOrder() {
        return Comparator
                .comparing(Performance::getOverallScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PerformanceSort::totalSolved, Comparator.reverseOrder())
                .thenComparing(p -> rollKey(p.getStudent() == null ? null : p.getStudent().getRollNumber()));
    }

    /**
     * Orders {@link Student} entities by their (optional) performance, which
     * is looked up from the supplied student-id to performance map. Used by
     * the year report, which iterates students rather than performances.
     */
    public static Comparator<Student> performanceOrderByStudent(Map<UUID, Performance> byStudent) {
        return Comparator
                .comparing((Student s) -> overallScore(byStudent.get(s.getId())),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(s -> totalSolved(byStudent.get(s.getId())), Comparator.reverseOrder())
                .thenComparing(s -> rollKey(s.getRollNumber()));
    }

    public static Comparator<Student> rollNumberOrder() {
        return Comparator.comparing(s -> rollKey(s.getRollNumber()));
    }

    private static BigDecimal overallScore(Performance p) {
        return p == null ? null : p.getOverallScore();
    }

    private static String rollKey(String rollNumber) {
        return rollNumber == null ? "" : rollNumber.toLowerCase();
    }
}
