package com.codetrack.backend.service;

import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final StudentRepository studentRepository;
    private final CodingProfileRepository codingProfileRepository;
    private final PerformanceRepository performanceRepository;

    public byte[] generateStudentReport(UUID studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
        CodingProfile profile = codingProfileRepository.findByStudentId(studentId).orElse(null);
        Performance performance = performanceRepository.findByStudentId(studentId).orElse(null);

        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(title("CodeTrack — Student Readiness Report"));
            document.add(new Paragraph("Generated: " + Instant.now()));
            document.add(new Paragraph(" "));

            document.add(heading("Student"));
            document.add(new Paragraph("Name: " + student.getName()));
            document.add(new Paragraph("Roll Number: " + student.getRollNumber()));
            document.add(new Paragraph("Email: " + student.getEmail()));
            document.add(new Paragraph("Branch / Year / Section: "
                    + nvl(student.getBranch()) + " / " + nvl(student.getYear()) + " / " + nvl(student.getSection())));
            document.add(new Paragraph(" "));

            document.add(heading("Platform Usernames"));
            if (profile == null) {
                document.add(new Paragraph("None configured"));
            } else {
                document.add(new Paragraph("LeetCode: " + nvl(profile.getLeetcodeUsername())));
                document.add(new Paragraph("Codeforces: " + nvl(profile.getCodeforcesUsername())));
                document.add(new Paragraph("CodeChef: " + nvl(profile.getCodechefUsername())));
            }
            document.add(new Paragraph(" "));

            document.add(heading("Performance Snapshot"));
            if (performance == null) {
                document.add(new Paragraph("No performance data yet — run a sync first."));
            } else {
                document.add(new Paragraph("Overall Readiness Score: " + nvl(performance.getOverallScore()) + " / 10"));
                document.add(new Paragraph("Consistency Score: " + nvl(performance.getConsistencyScore()) + " / 10"));
                document.add(new Paragraph("Last Synced: " + nvl(performance.getLastUpdated())));
                document.add(new Paragraph(" "));

                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                table.addCell(cell("Metric", true));
                table.addCell(cell("Value", true));
                table.addCell(cell("Notes", true));
                addRow(table, "LeetCode Rating", performance.getLeetcodeRating(), "Contest rating");
                addRow(table, "LeetCode Solved", performance.getLeetcodeSolved(),
                        "E/M/H = " + nvl(performance.getLeetcodeEasy()) + "/" + nvl(performance.getLeetcodeMedium())
                                + "/" + nvl(performance.getLeetcodeHard()));
                addRow(table, "Codeforces Rating", performance.getCodeforcesRating(), "Current rating");
                addRow(table, "CodeChef Rating", performance.getCodechefRating(), "Current rating");
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF report");
        }
    }

    private void addRow(PdfPTable table, String label, Object value, String notes) {
        table.addCell(cell(label, false));
        table.addCell(cell(nvl(value), false));
        table.addCell(cell(notes, false));
    }

    @Transactional(readOnly = true)
    public byte[] generateCollegeReport() {
        List<Performance> all = performanceRepository.findAll();
        List<Performance> scored = all.stream()
                .filter(p -> p.getOverallScore() != null)
                .toList();

        BigDecimal avgOverall = scored.isEmpty() ? BigDecimal.ZERO
                : scored.stream().map(Performance::getOverallScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgConsistency = scored.isEmpty() ? BigDecimal.ZERO
                : scored.stream().map(Performance::getConsistencyScore)
                        .filter(c -> c != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scored.stream()
                                        .map(Performance::getConsistencyScore).filter(c -> c != null).count()),
                                2, RoundingMode.HALF_UP);
        long ready = scored.stream()
                .filter(p -> p.getOverallScore().compareTo(new BigDecimal("7.00")) >= 0)
                .count();
        long atRisk = scored.stream()
                .filter(p -> p.getOverallScore().compareTo(new BigDecimal("4.00")) < 0)
                .count();

        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(title("CodeTrack — College Summary Report"));
            document.add(new Paragraph("Generated: " + Instant.now()));
            document.add(new Paragraph(" "));

            document.add(heading("Overview"));
            docLine(document, "Total Students", studentRepository.count());
            docLine(document, "Students Synced", all.size());
            docLine(document, "Average Overall Score", avgOverall + " / 10");
            docLine(document, "Average Consistency", avgConsistency + " / 10");
            docLine(document, "Placement Ready (score \u2265 7)", ready);
            docLine(document, "At-Risk Students (score < 4)", atRisk);
            document.add(new Paragraph(" "));

            List<Performance> sorted = scored.stream()
                    .sorted(Comparator.comparing(Performance::getOverallScore).reversed())
                    .toList();
            if (sorted.isEmpty()) {
                document.add(new Paragraph("No scored students yet — run syncs to populate the report."));
            } else {
                document.add(heading("Ranked Students"));
                PdfPTable table = new PdfPTable(8);
                table.setWidthPercentage(100);
                for (String h : new String[]{"Roll", "Name", "Branch", "Overall", "Consistency", "LC Solved", "CF", "CC"}) {
                    table.addCell(cell(h, true));
                }
                for (int i = 0; i < sorted.size(); i++) {
                    Performance p = sorted.get(i);
                    table.addCell(cell(String.valueOf(i + 1), false));
                    table.addCell(cell(p.getStudent().getRollNumber(), false));
                    table.addCell(cell(p.getStudent().getName(), false));
                    table.addCell(cell(nvl(p.getStudent().getBranch()), false));
                    table.addCell(cell(nvl(p.getOverallScore()), false));
                    table.addCell(cell(nvl(p.getConsistencyScore()), false));
                    table.addCell(cell(nvl(p.getLeetcodeSolved()), false));
                    table.addCell(cell(nvl(p.getCodechefRating()), false));
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate college report");
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateBranchReport(String branch) {
        List<Performance> all = performanceRepository.findAll().stream()
                .filter(p -> p.getStudent().getBranch() != null && p.getStudent().getBranch().equalsIgnoreCase(branch))
                .sorted(Comparator.comparing(Performance::getOverallScore,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(title("CodeTrack — Branch Report: " + branch));
            document.add(new Paragraph("Generated: " + Instant.now()));
            document.add(new Paragraph(" "));

            document.add(heading("Students"));
            if (all.isEmpty()) {
                document.add(new Paragraph("No students in this branch."));
            } else {
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                for (String h : new String[]{"Roll", "Name", "Overall", "Consistency", "LC", "CF", "CC"}) {
                    table.addCell(cell(h, true));
                }
                for (Performance p : all) {
                    table.addCell(cell(p.getStudent().getRollNumber(), false));
                    table.addCell(cell(p.getStudent().getName(), false));
                    table.addCell(cell(nvl(p.getOverallScore()), false));
                    table.addCell(cell(nvl(p.getConsistencyScore()), false));
                    table.addCell(cell(nvl(p.getLeetcodeSolved()), false));
                    table.addCell(cell(nvl(p.getCodeforcesRating()), false));
                    table.addCell(cell(nvl(p.getCodechefRating()), false));
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate branch report");
        }
    }

    private void docLine(Document document, String label, Object value) {
        document.add(new Paragraph(label + ": " + nvl(value)));
    }

    private PdfPCell cell(String text, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text));
        c.setPadding(6);
        if (header) {
            c.setBackgroundColor(new Color(79, 70, 229));
            c.setPhrase(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        }
        return c;
    }

    private Paragraph title(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(79, 70, 229));
        return new Paragraph(text, font);
    }

    private Paragraph heading(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(60, 60, 60));
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(6);
        return p;
    }

    private String nvl(Object value) {
        return value == null ? "—" : value.toString();
    }
}
