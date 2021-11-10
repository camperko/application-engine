package com.netgrif.workflow.workflow.service.interfaces;

import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.filters.FilterImportExport;
import com.netgrif.workflow.filters.FilterImportExportList;
import com.netgrif.workflow.petrinet.domain.dataset.FileFieldValue;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.IllegalFilterFileException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;


/**
 * Interface which provides methods for filter import and export.
 */

public interface IFilterImportExportService {

    FileFieldValue exportFiltersToFile(Collection<String> filtersToExport) throws IOException;

    FilterImportExportList exportFilters(Collection<String> filtersToExport);

    List<String> importFilters() throws IOException, IllegalFilterFileException;

    List<String> importFilters(FilterImportExportList filters) throws IOException;

    FilterImportExport createExportClass(Case filter);

    void createFilterImport(User author);

    void createFilterExport(User author);

    void changeFilterField(List<String> filterFields);
}
