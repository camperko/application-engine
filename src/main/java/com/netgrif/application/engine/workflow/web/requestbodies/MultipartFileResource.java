package com.netgrif.application.engine.workflow.web.requestbodies;

import org.springframework.core.io.ByteArrayResource;

public class MultipartFileResource extends ByteArrayResource {

    private String fileName;

    public MultipartFileResource(byte[] inputStream, String fileName) {
        super(inputStream);
        this.fileName = fileName;
    }

    @Override
    public String getFilename() {
        return this.fileName;
    }
}
