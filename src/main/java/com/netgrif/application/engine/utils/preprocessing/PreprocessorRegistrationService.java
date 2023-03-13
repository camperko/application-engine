package com.netgrif.application.engine.utils.preprocessing;

import com.netgrif.application.engine.importer.model.Document;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
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

    public Document doPreprocessing(Document document, InputStream xml) throws IOException, JAXBException {
        if (document.getPreprocessor() != null && !document.getPreprocessor().equals("")) {
            String url = REGISTRATION_SERVICE_ADDRESS + document.getPreprocessor();
            ResponseEntity<RegistrationDataResponse> responseData = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), RegistrationDataResponse.class
            );

            if (responseData.getBody() == null || responseData.getBody().getStringId() == null) {
                return document;
            }

            HttpHeaders parts = new HttpHeaders();
            parts.setContentType(MediaType.APPLICATION_XML);
            final HttpEntity<MultipartFileResource> partsEntity = new HttpEntity<>(
                    new MultipartFileResource(IOUtils.toByteArray(xml), PREPROCESSING_FILE_NAME), parts
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> requestMap = new LinkedMultiValueMap<>();
            requestMap.add("file", partsEntity);

            ResponseEntity<ByteArrayResource> response = restTemplate.exchange(
                    responseData.getBody().getAddress(), HttpMethod.POST, new HttpEntity<>(requestMap, headers), ByteArrayResource.class
            );

            JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            return (Document) jaxbUnmarshaller.unmarshal(new ByteArrayInputStream(response.getBody().getByteArray()));
        }
        return document;
    }
}
