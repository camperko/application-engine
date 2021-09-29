package com.netgrif.workflow.startup

import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.petrinet.domain.I18nString
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService
import com.netgrif.workflow.workflow.domain.Case
import com.netgrif.workflow.petrinet.domain.PetriNet
import com.netgrif.workflow.workflow.domain.QCase
import com.netgrif.workflow.workflow.domain.QTask
import com.netgrif.workflow.workflow.domain.Task
import com.netgrif.workflow.workflow.service.interfaces.IDataService
import com.netgrif.workflow.workflow.service.interfaces.ITaskService
import com.netgrif.workflow.workflow.service.interfaces.IWorkflowService
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Slf4j
@Component
class DefaultFiltersRunner extends AbstractOrderedCommandLineRunner {

    private static final String AUTO_CREATE_TRANSITION = "auto_create"

    private static final String FILTER_TYPE_FIELD_ID = "filter_type"
    private static final String FILTER_ORIGIN_VIEW_ID_FIELD_ID = "origin_view_id"
    private static final String FILTER_VISIBILITY_FIELD_ID = "visibility"
    private static final String FILTER_FIELD_ID = "filter"
    private static final String FILTER_I18N_TITLE_FIELD_ID = "i18n_filter_name"
    private static final String GERMAN_ISO_3166_CODE = "de"
    private static final String SLOVAK_ISO_3166_CODE = "sk"

    private static final String FILTER_TYPE_CASE = "Case"
    private static final String FILTER_TYPE_TASK = "Task"

    private static final String FILTER_VISIBILITY_PUBLIC = "public"

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private IWorkflowService workflowService

    @Autowired
    private IUserService userService

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IDataService dataService;

    @Override
    void run(String... args) throws Exception {
        createCaseFilter("All cases", "assignment", "", FILTER_VISIBILITY_PUBLIC, "", [], [
                "predicateMetadata": [],
                "searchCategories": []
        ], [
                (GERMAN_ISO_3166_CODE): "Alle Fälle",
                (SLOVAK_ISO_3166_CODE): "Všetky prípady"
        ])
        createCaseFilter("My cases", "assignment_ind", "", FILTER_VISIBILITY_PUBLIC, "(author:<<me>>)", [], [
                "predicateMetadata": [[["category": "case_author", "configuration": ["operator":"equals"], "values":[["text":"search.category.userMe", value:["<<me>>"]]]]]],
                "searchCategories": ["case_author"]
        ], [
                (GERMAN_ISO_3166_CODE): "Meine Fälle",
                (SLOVAK_ISO_3166_CODE): "Moje prípady"
        ])

        createTaskFilter("All tasks", "library_add_check", "", FILTER_VISIBILITY_PUBLIC, "", [], [
                "predicateMetadata": [],
                "searchCategories": []
        ], [
                (GERMAN_ISO_3166_CODE): "Alle Aufgaben",
                (SLOVAK_ISO_3166_CODE): "Všetky úlohy"
        ])
        createTaskFilter("My tasks", "account_box", "", FILTER_VISIBILITY_PUBLIC, "(userId:<<me>>)", [], [
                "predicateMetadata": [[["category": "task_assignee", "configuration": ["operator":"equals"], "values":[["text":"search.category.userMe", value:["<<me>>"]]]]]],
                "searchCategories": ["task_assignee"]
        ], [
                (GERMAN_ISO_3166_CODE): "Meine Aufgaben",
                (SLOVAK_ISO_3166_CODE): "Moje úlohy"
        ])
    }

    /**
     * Creates a new case filter filter process instance
     * @param title unique title of the default filter
     * @param icon material icon identifier of the default filter
     * @param filterOriginViewId viewID of the view the filter originated in
     * @param filterVisibility filter visibility
     * @param filterQuery the elastic query string query used by the filter
     * @param allowedNets list of process identifiers allowed for search categories metadata generation
     * @param filterMetadata metadata of the serialised filter as generated by the frontend
     * @param titleTranslations a map of locale codes to translated strings for the filter title
     * @param withDefaultCategories whether the default search categories should be merged with the search categories specified in the metadata
     * @param inheritBaseAllowedNets whether the base allowed nets should be merged with the allowed nets specified in the filter field
     * @return an empty Optional if the filter process does not exist. An existing filter process instance if a filter process instance with the same name already exists. A new filter process instance if not.
     */
    public Optional<Case> createCaseFilter(
            String title,
            String icon,
            String filterOriginViewId,
            String filterVisibility,
            String filterQuery,
            List<String> allowedNets,
            Map<String, Object> filterMetadata,
            Map<String, String> titleTranslations,
            boolean withDefaultCategories = true,
            boolean inheritBaseAllowedNets = true
    ) {
        return createFilter(title, icon, FILTER_TYPE_CASE, filterOriginViewId, filterVisibility, filterQuery, allowedNets, filterMetadata, titleTranslations, withDefaultCategories, inheritBaseAllowedNets)
    }

    /**
     * Creates a new task filter filter process instance
     * @param title unique title of the default filter
     * @param icon material icon identifier of the default filter
     * @param filterOriginViewId viewID of the view the filter originated in
     * @param filterVisibility filter visibility
     * @param filterQuery the elastic query string query used by the filter
     * @param allowedNets list of process identifiers allowed for search categories metadata generation
     * @param filterMetadata metadata of the serialised filter as generated by the frontend
     * @param titleTranslations a map of locale codes to translated strings for the filter title
     * @param withDefaultCategories whether the default search categories should be merged with the search categories specified in the metadata
     * @param inheritBaseAllowedNets whether the base allowed nets should be merged with the allowed nets specified in the filter field
     * @return an empty Optional if the filter process does not exist. An existing filter process instance if a filter process instance with the same name already exists. A new filter process instance if not.
     */
    public Optional<Case> createTaskFilter(
            String title,
            String icon,
            String filterOriginViewId,
            String filterVisibility,
            String filterQuery,
            List<String> allowedNets,
            Map<String, Object> filterMetadata,
            Map<String, String> titleTranslations,
            boolean withDefaultCategories = true,
            boolean inheritBaseAllowedNets = true
    ) {
        return createFilter(title, icon, FILTER_TYPE_TASK, filterOriginViewId, filterVisibility, filterQuery, allowedNets, filterMetadata, titleTranslations, withDefaultCategories, inheritBaseAllowedNets)
    }

    private Optional<Case> createFilter(
            String title,
            String icon,
            String filterType,
            String filterOriginViewId,
            String filterVisibility,
            String filterQuery,
            List<String> allowedNets,
            Map<String, Object> filterMetadata,
            Map<String, String> titleTranslations,
            boolean withDefaultCategories,
            boolean inheritBaseAllowedNets
    ) {
        return createFilter(
                title,
                icon,
                filterType,
                filterOriginViewId,
                filterVisibility,
                filterQuery,
                allowedNets,
                filterMetadata << ["filterType": filterType, "defaultSearchCategories": withDefaultCategories, "inheritAllowedNets": inheritBaseAllowedNets],
                titleTranslations
        )
    }

    private Optional<Case> createFilter(
            String title,
            String icon,
            String filterType,
            String filterOriginViewId,
            String filterVisibility,
            String filterQuery,
            List<String> allowedNets,
            Map<String, Object> filterMetadata,
            Map<String, String> titleTranslations
    ) {
        PetriNet filterNet = this.petriNetService.getNewestVersionByIdentifier('filter')
        if (filterNet == null) {
            return Optional.empty()
        }

        def systemUser = this.userService.getLoggedOrSystem()

        def existingFilter = this.workflowService.search(QCase.case$.processIdentifier.eq("filter") & QCase.case$.author.id.eq(systemUser.getId()) & QCase.case$.title.eq(title), PageRequest.of(0, 1))
        if (existingFilter.totalElements == 1) {
            return Optional.of(existingFilter.getContent()[0])
        }

        Case filterCase = this.workflowService.createCase(filterNet.getStringId(), title, null, systemUser.transformToLoggedUser())
        filterCase.setIcon(icon)
        filterCase = this.workflowService.save(filterCase)
        Task newFilterTask = this.taskService.searchOne(QTask.task.transitionId.eq(AUTO_CREATE_TRANSITION).and(QTask.task.caseId.eq(filterCase.getStringId())))
        this.taskService.assignTask(newFilterTask, this.userService.getLoggedOrSystem())
        this.dataService.setData(newFilterTask, ImportHelper.populateDataset([
            (FILTER_TYPE_FIELD_ID): [
                "type": "enumeration_map",
                "value": filterType
            ],
            (FILTER_VISIBILITY_FIELD_ID): [
                "type": "enumeration_map",
                "value": filterVisibility
            ],
            (FILTER_ORIGIN_VIEW_ID_FIELD_ID): [
                    "type": "text",
                    "value": filterOriginViewId
            ],
            (FILTER_FIELD_ID): [
                    "type": "filter",
                    "value": filterQuery,
                    "allowedNets": allowedNets,
                    "filterMetadata": filterMetadata
            ]
        ]))

        I18nString translatedTitle = new I18nString(title)
        titleTranslations.forEach({locale, translation -> translatedTitle.addTranslation(locale, translation)})

        filterCase = this.workflowService.findOne(filterCase.getStringId())
        filterCase.dataSet[FILTER_I18N_TITLE_FIELD_ID].value = translatedTitle
        workflowService.save(filterCase)

        this.taskService.finishTask(newFilterTask, this.userService.getLoggedOrSystem())
        return Optional.of(this.workflowService.findOne(filterCase.getStringId()))
    }
}
