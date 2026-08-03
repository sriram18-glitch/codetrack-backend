package com.codetrack.backend.service;

import com.codetrack.backend.dto.CsvImportResult;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvService {

    private static final String[] HEADERS = {"rollNumber", "name", "email", "branch", "year", "section", "phone"};

    private final StudentRepository studentRepository;
    private final PerformanceRepository performanceRepository;

    @Transactional
    public CsvImportResult importStudents(InputStream input) {
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    line = stripBom(line);
                    firstLine = false;
                }
                lineNumber++;
                if (line.isBlank()) continue;
                List<String> fields = parseCsvLine(line);
                if (lineNumber == 1 && !fields.isEmpty() && "rollNumber".equalsIgnoreCase(fields.get(0).trim())) {
                    continue; // header row
                }
                try {
                    createFromFields(fields, lineNumber);
                    imported++;
                } catch (IllegalArgumentException ex) {
                    failed++;
                    errors.add("Line " + lineNumber + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read CSV file: " + ex.getMessage());
        }

        log.info("CSV import complete: {} imported, {} failed", imported, failed);
        return new CsvImportResult(imported, failed, errors);
    }

    @Transactional(readOnly = true)
    public String exportCsv() {
        List<Student> students = studentRepository.findAll();
        Map<Object, Performance> performances = performanceRepository.findAll().stream()
                .collect(Collectors.toMap(p -> p.getStudent().getId(), Function.identity()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append(",overallScore,leetcodeSolved,codeforcesRating,codechefRating\n");

        for (Student student : students) {
            Performance p = performances.get(student.getId());
            sb.append(csv(student.getRollNumber())).append(',')
                    .append(csv(student.getName())).append(',')
                    .append(csv(student.getEmail())).append(',')
                    .append(csv(student.getBranch())).append(',')
                    .append(student.getYear() == null ? "" : student.getYear()).append(',')
                    .append(csv(student.getSection())).append(',')
                    .append(csv(student.getPhone())).append(',')
                    .append(p == null || p.getOverallScore() == null ? "" : p.getOverallScore()).append(',')
                    .append(p == null || p.getLeetcodeSolved() == null ? "" : p.getLeetcodeSolved()).append(',')
                    .append(p == null || p.getCodeforcesRating() == null ? "" : p.getCodeforcesRating()).append(',')
                    .append(p == null || p.getCodechefRating() == null ? "" : p.getCodechefRating()).append('\n');
        }
        return sb.toString();
    }

    private String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private void createFromFields(List<String> fields, int lineNumber) {
        if (fields.size() < 3) {
            throw new IllegalArgumentException("Row must contain at least roll number, name and email");
        }
        String rollNumber = fields.get(0).trim();
        String name = fields.get(1).trim();
        String email = fields.get(2).trim();
        String branch = fields.size() > 3 ? fields.get(3).trim() : "";
        Integer year = fields.size() > 4 && !fields.get(4).trim().isEmpty()
                ? parseInt(fields.get(4).trim(), "year") : null;
        String section = fields.size() > 5 ? fields.get(5).trim() : "";
        String phone = fields.size() > 6 ? fields.get(6).trim() : "";

        if (rollNumber.isEmpty() || name.isEmpty() || email.isEmpty()) {
            throw new IllegalArgumentException("Roll number, name and email are required");
        }
        if (studentRepository.existsByRollNumberIgnoreCase(rollNumber)) {
            throw new IllegalArgumentException("Roll number '" + rollNumber + "' already exists");
        }
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email '" + email + "' already exists");
        }

        Student student = Student.builder()
                .rollNumber(rollNumber)
                .name(name)
                .email(email)
                .branch(branch.isEmpty() ? null : branch)
                .year(year)
                .section(section.isEmpty() ? null : section)
                .phone(phone.isEmpty() ? null : phone)
                .build();
        studentRepository.save(student);
    }

    private Integer parseInt(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + field + " value '" + value + "'");
        }
    }

    private String csv(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }
}
