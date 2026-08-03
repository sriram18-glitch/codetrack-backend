package com.codetrack.backend.controller;

import com.codetrack.backend.dto.CreateStudentRequest;
import com.codetrack.backend.dto.StudentResponse;
import com.codetrack.backend.dto.UpdateStudentRequest;
import com.codetrack.backend.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Students", description = "Manage student records (admin only)")
public class StudentController {

    private final StudentService studentService;

    @PostMapping
    @Operation(summary = "Add a new student")
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(request));
    }

    @GetMapping
    @Operation(summary = "List all students (optionally filter with ?q=name/roll/email/branch)")
    public ResponseEntity<List<StudentResponse>> listStudents(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(studentService.searchStudents(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a student by id")
    public ResponseEntity<StudentResponse> getStudent(@PathVariable UUID id) {
        return ResponseEntity.ok(studentService.getStudent(id));
    }

    @GetMapping("/roll/{rollNumber}")
    @Operation(summary = "Get a student by roll number")
    public ResponseEntity<StudentResponse> getByRollNumber(@PathVariable String rollNumber) {
        return ResponseEntity.ok(studentService.getByRollNumber(rollNumber));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing student")
    public ResponseEntity<StudentResponse> updateStudent(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateStudentRequest request) {
        return ResponseEntity.ok(studentService.updateStudent(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a student (cascades to coding profile and performance data)")
    public ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}
