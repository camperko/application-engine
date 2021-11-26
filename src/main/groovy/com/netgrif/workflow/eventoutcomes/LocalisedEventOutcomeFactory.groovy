package com.netgrif.workflow.eventoutcomes

import com.netgrif.workflow.workflow.domain.eventoutcomes.EventOutcome
import com.netgrif.workflow.workflow.web.responsebodies.eventoutcomes.base.LocalisedEventOutcome

class LocalisedEventOutcomeFactory {

    private static final String localisedEventOutcomesPackage = "com.netgrif.workflow.workflow.web.responsebodies.eventoutcomes."

    static LocalisedEventOutcome from(EventOutcome outcome, Locale locale){
//        todo test https://github.com/ronmamo/reflections
        return Class.forName(localisedEventOutcomesPackage + "Localised${outcome.class.simpleName}").newInstance(outcome, locale)
    }
}
