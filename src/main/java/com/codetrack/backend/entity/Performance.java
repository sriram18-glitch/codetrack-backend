package com.codetrack.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "performance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(name = "overall_score", precision = 4, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "consistency_score", precision = 4, scale = 2)
    private BigDecimal consistencyScore;

    @Column(name = "leetcode_rating")
    private Integer leetcodeRating;

    @Column(name = "leetcode_solved")
    private Integer leetcodeSolved;

    @Column(name = "leetcode_easy")
    private Integer leetcodeEasy;

    @Column(name = "leetcode_medium")
    private Integer leetcodeMedium;

    @Column(name = "leetcode_hard")
    private Integer leetcodeHard;

    @Column(name = "codeforces_rating")
    private Integer codeforcesRating;

    @Column(name = "codechef_rating")
    private Integer codechefRating;

    @Column(name = "last_updated")
    private Instant lastUpdated;
}
