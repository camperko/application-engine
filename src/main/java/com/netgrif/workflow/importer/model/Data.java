
package com.netgrif.workflow.importer.model;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element ref="{}id"/&gt;
 *         &lt;element ref="{}title"/&gt;
 *         &lt;element ref="{}placeholder" minOccurs="0"/&gt;
 *         &lt;element ref="{}desc" minOccurs="0"/&gt;
 *         &lt;element ref="{}values" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element ref="{}valid" minOccurs="0"/&gt;
 *         &lt;element ref="{}init" minOccurs="0"/&gt;
 *         &lt;element ref="{}encryption" minOccurs="0"/&gt;
 *         &lt;element ref="{}action" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element ref="{}documentRef" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="type" type="{}data_type" /&gt;
 *       &lt;attribute name="immediate" type="{http://www.w3.org/2001/XMLSchema}boolean" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "id",
    "title",
    "placeholder",
    "desc",
    "values",
    "valid",
    "init",
    "encryption",
    "action",
    "documentRef"
})
@XmlRootElement(name = "data")
public class Data {

    protected long id;
    @XmlElement(required = true)
    protected String title;
    protected String placeholder;
    protected String desc;
    protected List<String> values;
    protected String valid;
    protected String init;
    protected EncryptionType encryption;
    protected List<ActionType> action;
    protected DocumentRef documentRef;
    @XmlAttribute(name = "type")
    protected DataType type;
    @XmlAttribute(name = "immediate")
    protected Boolean immediate;

    /**
     * Gets the value of the id property.
     * 
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     */
    public void setId(long value) {
        this.id = value;
    }

    /**
     * Gets the value of the title property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the value of the title property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTitle(String value) {
        this.title = value;
    }

    /**
     * Gets the value of the placeholder property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Sets the value of the placeholder property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPlaceholder(String value) {
        this.placeholder = value;
    }

    /**
     * Gets the value of the desc property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDesc() {
        return desc;
    }

    /**
     * Sets the value of the desc property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDesc(String value) {
        this.desc = value;
    }

    /**
     * Gets the value of the values property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the values property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getValues().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getValues() {
        if (values == null) {
            values = new ArrayList<String>();
        }
        return this.values;
    }

    /**
     * Gets the value of the valid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getValid() {
        return valid;
    }

    /**
     * Sets the value of the valid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setValid(String value) {
        this.valid = value;
    }

    /**
     * Gets the value of the init property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getInit() {
        return init;
    }

    /**
     * Sets the value of the init property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setInit(String value) {
        this.init = value;
    }

    /**
     * Gets the value of the encryption property.
     * 
     * @return
     *     possible object is
     *     {@link EncryptionType }
     *     
     */
    public EncryptionType getEncryption() {
        return encryption;
    }

    /**
     * Sets the value of the encryption property.
     * 
     * @param value
     *     allowed object is
     *     {@link EncryptionType }
     *     
     */
    public void setEncryption(EncryptionType value) {
        this.encryption = value;
    }

    /**
     * Gets the value of the action property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the action property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getAction().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ActionType }
     * 
     * 
     */
    public List<ActionType> getAction() {
        if (action == null) {
            action = new ArrayList<ActionType>();
        }
        return this.action;
    }

    /**
     * Gets the value of the documentRef property.
     * 
     * @return
     *     possible object is
     *     {@link DocumentRef }
     *     
     */
    public DocumentRef getDocumentRef() {
        return documentRef;
    }

    /**
     * Sets the value of the documentRef property.
     * 
     * @param value
     *     allowed object is
     *     {@link DocumentRef }
     *     
     */
    public void setDocumentRef(DocumentRef value) {
        this.documentRef = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link DataType }
     *     
     */
    public DataType getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link DataType }
     *     
     */
    public void setType(DataType value) {
        this.type = value;
    }

    /**
     * Gets the value of the immediate property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isImmediate() {
        return immediate;
    }

    /**
     * Sets the value of the immediate property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setImmediate(Boolean value) {
        this.immediate = value;
    }

}
