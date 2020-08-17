package com.netgrif.workflow.pdf.generator.config.types;

import lombok.Getter;
import lombok.Setter;

public enum PdfPageNumberFormat {

    SIMPLE(""),
    SLASH("/");

    @Getter
    @Setter
    private String format;


    PdfPageNumberFormat(String s) {
        format = s;
    }

}
