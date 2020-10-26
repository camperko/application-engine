package com.netgrif.workflow.pdf.generator.service.interfaces;

import com.netgrif.workflow.pdf.generator.config.PdfResource;
import com.netgrif.workflow.pdf.generator.domain.PdfField;
import com.netgrif.workflow.petrinet.domain.DataFieldLogic;
import com.netgrif.workflow.petrinet.domain.DataGroup;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.Transition;
import com.netgrif.workflow.workflow.domain.DataField;

import java.util.List;
import java.util.Map;

public interface IPdfDataHelper {

    void setPetriNet(PetriNet petriNet);
    void setTransition(Transition transition);
    void setDataGroups(Map<String, DataGroup> dataGroups);
    void setDataSet(Map<String, DataField> dataSet);
    void setFieldLogicMap(Map<String, DataFieldLogic> fieldLogicMap);
    void setPdfFields(List<PdfField> fields);
    List<PdfField> getPdfFields();
    void setupDataHelper(PdfResource resource);
    void generateTitleField();
    void generatePdfFields();
    void correctFieldsPosition();
}
