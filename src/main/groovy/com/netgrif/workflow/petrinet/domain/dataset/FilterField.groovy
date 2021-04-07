package com.netgrif.workflow.petrinet.domain.dataset

import org.springframework.data.mongodb.core.mapping.Document

@Document
class FilterField extends FieldWithAllowedNets<String>  {

    /**
     * Serialized information necessary for the restoration of the advanced search frontend GUI.
     *
     * Backend shouldn't need to interact with this attribute
     */
    private Object filterMetadata

    FilterField() {
        super()
        allowedNets = new ArrayList<>()
    }

    FilterField(List<String> allowedNets) {
        super(allowedNets)
    }

    @Override
    FieldType getType() {
        return FieldType.FILTER
    }

    @Override
    Field clone() {
        FilterField clone = new FilterField()
        super.clone(clone)
        clone.filterMetadata = this.filterMetadata

        return clone
    }

    Object getFilterMetadata() {
        return filterMetadata
    }

    void setFilterMetadata(Object filterMetadata) {
        this.filterMetadata = filterMetadata
    }
}
