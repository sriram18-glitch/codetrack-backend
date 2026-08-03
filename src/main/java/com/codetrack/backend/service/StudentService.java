package com.codetrack.backend.service;

import com.codetrack.backend.dto.CreateStudentRequest;
import com.codetrack.backend.dto.StudentResponse;
import com.codetrack.backend.dto.UpdateStudentRequest;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    @Transactional
    public StudentResponse createStudent(CreateStudentRequest request) {
        if (studentRepository.existsByRollNumberIgnoreCase(request.rollNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this roll number already exists");
        }
        if (studentRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this email already exists");
        }

        Student student = Student.builder()
                .rollNumber(request.rollNumber())
                .name(request.name())
                .email(request.email())
                .branch(request.branch())
                .year(request.year())
                .section(request.section())
                .phone(request.phone())
                .build();

        student = studentRepository.save(student);
        log.info("Student created: id={}, rollNumber={}", student.getId(), student.getRollNumber());

        return toResponse(student);
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> listStudents() {
        return studentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentResponse getStudent(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public StudentResponse getByRollNumber(String rollNumber) {
        return studentRepository.findByRollNumberIgnoreCase(rollNumber)
                .map(this::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student found with roll number " + rollNumber));
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> searchStudents(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return listStudents();
        }
        return studentRepository
                .findByRollNumberContainingIgnoreCaseOrNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrBranchContainingIgnoreCase(
                        q, q, q, q)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StudentResponse updateStudent(UUID id, UpdateStudentRequest request) {
        Student student = findById(id);

        if (!student.getRollNumber().equalsIgnoreCase(request.rollNumber())
                && studentRepository.existsByRollNumberIgnoreCase(request.rollNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this roll number already exists");
        }
        if (!student.getEmail().equalsIgnoreCase(request.email())
                && studentRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "A student with this email already exists");
        }

        student.setRollNumber(request.rollNumber());
        student.setName(request.name());
        student.setEmail(request.email());
        student.setBranch(request.branch());
        student.setYear(request.year());
        student.setSection(request.section());
        student.setPhone(request.phone());

        student = studentRepository.save(student);
        log.info("Student updated: id={}, rollNumber={}", student.getId(), student.getRollNumber());

        return toResponse(student);
    }

    @Transactional
    public void deleteStudent(UUID id) {
        Student student = findById(id);
        studentRepository.delete(student);
        log.info("Student deleted: id={}, rollNumber={}", student.getId(), student.getRollNumber());
    }

    private Student findById(UUID id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private StudentResponse toResponse(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getRollNumber(),
                student.getName(),
                student.getEmail(),
                student.getBranch(),
                student.getYear(),
                student.getSection(),
                student.getPhone(),
                student.getCreatedAt()
        );
    }
}
