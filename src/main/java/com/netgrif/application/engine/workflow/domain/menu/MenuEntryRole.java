package com.netgrif.application.engine.workflow.domain.menu;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.netgrif.application.engine.petrinet.domain.I18nString;
import com.netgrif.application.engine.workflow.domain.AuthorizationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.xml.bind.annotation.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "roleImportId",
        "netImportId"
})
public class MenuEntryRole {

    @XmlElement(required = true)
    protected String roleImportId;
    @XmlElement(required = true)
    protected String netImportId;
    @JacksonXmlProperty(localName = "type", isAttribute = true)
    protected AuthorizationType authorizationType;

}