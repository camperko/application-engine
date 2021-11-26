package com.netgrif.workflow.importer.service;

import com.netgrif.workflow.importer.model.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataValidator implements IDataValidator {

    @Override
    public void checkDeprecatedAttributes(Data data){
        validateAttribute(data.getView(), "view", data.getId());
        validateAttribute(data.getValid() != null && !data.getValid().isEmpty() ? data.getValid() : null, "valid", data.getId());
        validateAttribute(data.getFormat(), "format", data.getId());
        validateAttribute(data.getValues() != null && !data.getValues().isEmpty() ? data.getValues() : null, "values", data.getId());
    }
}
