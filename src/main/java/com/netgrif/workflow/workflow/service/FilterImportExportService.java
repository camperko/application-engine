package com.netgrif.workflow.workflow.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.common.collect.Lists;
import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.filters.FilterMetadataExport;
import com.netgrif.workflow.petrinet.domain.I18nString;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.dataset.FileFieldValue;
import com.netgrif.workflow.petrinet.domain.dataset.FilterField;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.startup.DefaultFiltersRunner;
import com.netgrif.workflow.workflow.domain.*;
import com.netgrif.workflow.workflow.domain.CustomFilterDeserializer;
import com.netgrif.workflow.filters.FilterImportExport;
import com.netgrif.workflow.filters.FilterImportExportList;
import com.netgrif.workflow.workflow.service.interfaces.IFilterImportExportService;
import com.netgrif.workflow.workflow.service.interfaces.ITaskService;
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.*;


@Service
public class FilterImportExportService implements IFilterImportExportService {

    private static final Logger log = LoggerFactory.getLogger(FilterImportExportService.class);

    private static final String EXPORT_NET_IDENTIFIER = "export_filters";
    private static final String IMPORT_NET_IDENTIFIER = "import_filters";

    private static final String UPLOAD_FILE_FIELD = "upload_file";

    private static final String FILTER_TYPE_CASE = "Case";
    private static final String FILTER_TYPE_TASK = "Task";

    private static final String IMPORT_FILTER_TRANSITION = "import_filter";

    private static final String FIELD_VISIBILITY = "visibility";
    private static final String FIELD_FILTER_TYPE = "filter_type";
    private static final String FIELD_FILTER = "filter";
    private static final String FIELD_NAME = "i18n_filter_name";

    @Autowired
    IUserService userService;

    @Autowired
    IWorkflowService workflowService;

    @Autowired
    IPetriNetService petriNetService;

    @Autowired
    DefaultFiltersRunner defaultFiltersRunner;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private FileStorageConfiguration fileStorageConfiguration;

    @Value("${nae.filter.export.file-name:filters.xml}")
    private String exportedFileName;

    @Override
    public void createFilterImport(User author) {
        createFilterCase("IMP_" + author.getFullName(), IMPORT_NET_IDENTIFIER, author.transformToLoggedUser());
    }

    @Override
    public void createFilterExport(User author) {
        createFilterCase("EXP_" + author.getFullName(), EXPORT_NET_IDENTIFIER, author.transformToLoggedUser());
    }

    private void createFilterCase(String title, String netIdentifier, LoggedUser loggedUser) {
        PetriNet filterImportNet = petriNetService.getNewestVersionByIdentifier(netIdentifier);
        workflowService.createCase(filterImportNet.getStringId(), title, "", loggedUser);
    }

    @Override
    public FileFieldValue exportFilters(Set<String> filtersToExport) throws IOException {
        log.info("Exporting selected filters");
        List<Case> selectedFilterCases = this.workflowService.findAllById(Lists.newArrayList(filtersToExport));
        FilterImportExportList filterList = new FilterImportExportList();
        selectedFilterCases.forEach(filter -> filterList.getFilters().add(createExportClass(filter)));
        return createXML(filterList);
    }

    @Override
    public List<String> importFilters() throws IOException, IllegalFilterFileException {
        FilterImportExportList filterList = loadFromXML();
        List<String> importedFiltersIds = new ArrayList<>();

        if (filterList == null) {
            throw new FileNotFoundException();
        }

        filterList.getFilters().forEach(filter -> {
            Optional<Case> filterCase = Optional.empty();
            if (filter.getType().equals(FILTER_TYPE_CASE)) {
                filterCase = defaultFiltersRunner.createCaseFilter(
                        filter.getFilterName().getDefaultValue(),
                        filter.getIcon(),
                        "",
                        filter.getVisibility(),
                        filter.getFilterValue(),
                        filter.getAllowedNets() != null ? filter.getAllowedNets() : new ArrayList<>(),
                        filter.getFilterMetadataExport().getMapObject(),
                        filter.getFilterName().getTranslations(),
                        filter.getFilterMetadataExport().getDefaultSearchCategories(),
                        filter.getFilterMetadataExport().getInheritAllowedNets(),
                        true
                );
            } else if (filter.getType().equals(FILTER_TYPE_TASK)) {
                filterCase = defaultFiltersRunner.createTaskFilter(
                        filter.getFilterName().getDefaultValue(),
                        filter.getIcon(),
                        "",
                        filter.getVisibility(),
                        filter.getFilterValue(),
                        filter.getAllowedNets() != null ? filter.getAllowedNets() : new ArrayList<>(),
                        filter.getFilterMetadataExport().getMapObject(),
                        filter.getFilterName().getTranslations(),
                        filter.getFilterMetadataExport().getDefaultSearchCategories(),
                        filter.getFilterMetadataExport().getInheritAllowedNets(),
                        true
                );
            }

            if (filterCase.isPresent()) {
                Task importFilterTask = taskService.searchOne(
                        QTask.task.transitionId.eq(IMPORT_FILTER_TRANSITION)
                                .and(QTask.task.caseId.eq(filterCase.get().getStringId()))
                );
                importedFiltersIds.add(importFilterTask.getStringId());
            }
        });

        return importedFiltersIds;
    }

    @Transactional
    protected FilterImportExportList loadFromXML() throws IOException, IllegalFilterFileException {
        Case exportCase = workflowService.searchOne(
                QCase.case$.processIdentifier.eq(IMPORT_NET_IDENTIFIER)
                        .and(QCase.case$.author.id.eq(userService.getLoggedUser().getId()))
        );

        FileFieldValue ffv = (FileFieldValue) exportCase.getDataSet().get(UPLOAD_FILE_FIELD).getValue();
        if (ffv == null) {
            throw new FileNotFoundException();
        }

        File f = new File(ffv.getPath());
        validateFilterXML(new FileInputStream(f));
        String importedFilter = inputStreamToString(new FileInputStream(f));
        SimpleModule module = new SimpleModule().addDeserializer(Object.class, CustomFilterDeserializer.getInstance());
        XmlMapper xmlMapper = (XmlMapper) new XmlMapper().registerModule(module);
        return xmlMapper.readValue(importedFilter, FilterImportExportList.class);
    }

    @Transactional
    protected FileFieldValue createXML(FilterImportExportList filters) throws IOException {
        String filePath = fileStorageConfiguration.getStoragePath() + "/filterExport/" + exportedFileName;
        File f = new File(filePath);
        f.getParentFile().mkdirs();

        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xmlMapper.writeValue(baos, filters);

        FileOutputStream fos = new FileOutputStream(f);
        baos.writeTo(fos);

        return new FileFieldValue(exportedFileName, filePath);
    }

    private FilterImportExport createExportClass(Case filter) {
        FilterImportExport exportFilter = new FilterImportExport();
        exportFilter.setTitle(filter.getTitle());
        exportFilter.setIcon(filter.getIcon());
        filter.getImmediateData().forEach(immediateData -> {
            switch (immediateData.getImportId()) {
                case FIELD_FILTER:
                    exportFilter.setFilterValue(((FilterField) immediateData).getValue());
                    exportFilter.setAllowedNets(((FilterField) immediateData).getAllowedNets());
                    exportFilter.setFilterMetadataExport(((FilterField) immediateData).getFilterMetadata());
                    break;
                case FIELD_VISIBILITY:
                    exportFilter.setVisibility(immediateData.getValue().toString());
                    break;
                case FIELD_FILTER_TYPE:
                    exportFilter.setType(immediateData.getValue().toString());
                    break;
                case FIELD_NAME:
                    exportFilter.setFilterName((I18nString) immediateData.getValue());
            }
        });
        return exportFilter;
    }

    private String inputStreamToString(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }

    private static void validateFilterXML(InputStream xml) throws IllegalFilterFileException {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(FilterImportExportService.class.getResource("/petriNets/filter_export_schema.xsd"));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xml));
        } catch(Exception ex) {
            throw new IllegalFilterFileException();
        }
    }
}



