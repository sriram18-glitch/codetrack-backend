package com.codetrack.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "coding_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    @Column(name = "leetcode_username", length = 100)
    private String leetcodeUsername;

    @Column(name = "codeforces_username", length = 100)
    private String codeforcesUsername;

    @Column(name = "codechef_username", length = 100)
    private String codechefUsername;
}
