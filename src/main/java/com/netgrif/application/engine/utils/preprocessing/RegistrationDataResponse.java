package com.netgrif.application.engine.utils.preprocessing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationDataResponse {

    private String stringId;

    private String name;

    private String address;

    private String description;

    private LocalDateTime creationDate;

    private LocalDateTime modificationDate;
}
