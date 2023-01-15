package com.netgrif.application.engine.workflow.service;

import com.netgrif.application.engine.importer.model.Document;
import com.netgrif.application.engine.workflow.web.requestbodies.MultipartFileResource;
import com.netgrif.application.engine.workflow.web.responsebodies.RegistrationDataResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;

@Service
public class PreprocessorRegistrationService {

    private RestTemplate restTemplate;

    @Value("${nae.registration-service.address}")
    private String REGISTRATION_SERVICE_ADDRESS;

    private static final String PREPROCESSING_FILE_NAME = "preprocessing_file";

    public PreprocessorRegistrationService() {
        this.restTemplate = new RestTemplate();
    }

    public Document doPreprocessing(Document document, InputStream xml) throws IOException {
        if (document.getPreprocessor() != null && !document.getPreprocessor().equals("")) {
            String url = REGISTRATION_SERVICE_ADDRESS + document.getPreprocessor();
            ResponseEntity<RegistrationDataResponse> responseData = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), RegistrationDataResponse.class
            );

            if (responseData.getBody() == null || responseData.getBody().getStringId() == null) {
                return document;
            }

            byte[] content = IOUtils.toByteArray(xml);
            Document doc = new Document();

            HttpHeaders parts = new HttpHeaders();
            parts.setContentType(MediaType.APPLICATION_XML);
            final HttpEntity<MultipartFileResource> partsEntity = new HttpEntity<>(new MultipartFileResource(content, PREPROCESSING_FILE_NAME), parts);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> requestMap = new LinkedMultiValueMap<>();
            requestMap.add("file", partsEntity);

            ResponseEntity<String> response = restTemplate.exchange(
                    responseData.getBody().getAddress(), HttpMethod.POST, new HttpEntity<>(requestMap, headers), String.class
            );

            // TODO: parse document from response (change response to xml file)
            return doc;
        }
        return document;
    }
}
