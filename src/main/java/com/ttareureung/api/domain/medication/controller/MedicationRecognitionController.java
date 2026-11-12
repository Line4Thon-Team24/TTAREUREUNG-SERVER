package com.ttareureung.api.domain.medication.controller;

import com.ttareureung.api.domain.medication.dto.RecognitionResponse;
import com.ttareureung.api.domain.medication.service.MedicationRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/medications")
@RequiredArgsConstructor
public class MedicationRecognitionController {

    private final MedicationRecognitionService recognitionService;

    @PostMapping(value = "/recognition", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecognitionResponse recognize(@RequestPart("file") MultipartFile file) {
        return recognitionService.recognize(file);
    }
}
