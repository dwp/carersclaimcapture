package uk.gov.dwp.carersallowance.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import uk.gov.dwp.carersallowance.session.IllegalFieldValueException;
import uk.gov.dwp.carersallowance.utils.Parameters;
import uk.gov.dwp.carersallowance.utils.xml.XPathMapping;
import uk.gov.dwp.carersallowance.utils.xml.XPathMappingList;
import uk.gov.dwp.carersallowance.utils.xml.XmlPrettyPrinter;

public class XmlBuilder {
    private static final String PATH_SEPARATOR = "/";

    private Document                      document;
    private Map<String, XPathMappingList> valueMappings;

    public XmlBuilder(String rootNodeName, Map<String, String> namespaces, Map<String, Object> values, Map<String, XPathMappingList> valueMappings) throws ParserConfigurationException {
        Parameters.validateMandatoryArgs(new Object[]{rootNodeName}, new String[]{"rootNodeName"});

        document = createDocument(rootNodeName, namespaces);
        this.valueMappings = valueMappings;

        add(values, null, document);
    }

    private Document createDocument(String rootNodeName, Map<String, String> namespaces) throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        Document doc = docBuilder.newDocument();
        Element rootNode = doc.createElement(rootNodeName);
        doc.appendChild(rootNode);

        if(namespaces != null) {
            for(Map.Entry<String, String> namespace : namespaces.entrySet()) {
                Attr attr = doc.createAttribute(namespace.getKey());
                attr.setValue(namespace.getValue());
                rootNode.setAttributeNode(attr);
            }
        }

        return doc;
    }

    /**
     * Render the contents of "values" as an XML document by iterating over XPathMappingList; which maps a named
     * value (in values) to a Node at a specific XPath location, populating the nodes with the corresponding values
     *
     * @param values
     * @param valueMappings
     * @param localRootNode XPath calculations use this as the root location, usually document, but can be different for FieldCollections
     */
    private void add(Map<String, Object> values, String mappingName, Node localRootNode) {
        if(values == null) {
            return;
        }

        XPathMappingList mappingList = valueMappings.get(mappingName);
        if(mappingList == null) {
            throw new IllegalArgumentException("Unknown mapping: " + mappingName);
        }

        List<XPathMapping> list = mappingList.getList();
        for(XPathMapping mapping : list) {
            String valueKey = mapping.getValue();
            Object value = values.get(valueKey);
            String xpath = mapping.getXpath();
            if(StringUtils.isNotBlank(xpath) && isValueEmpty(value) == false) {
                if(value instanceof String) {
                    add((String)value, mapping.getXpath(), true, localRootNode);   // create leaf node
                } else if(value instanceof List){
                    // field collection, we can't reliably assert the parameterized types, so will go with <?>
                    List<Map<String, String>> fieldCollectionList = castFieldCollectionList(value);
                    add(fieldCollectionList, mapping.getXpath());
                } else {
                    throw new IllegalFieldValueException("Unsupported value class: " + value.getClass().getName(), (String)null, (String[])null);
                }
            }
        }
    }

    /**
     * Add a single node value to the existing document merging in the XPath to the existing structure
     * @param value e.g. DLA
     * @param Xpath e.g. DWPBody/DWPCATransaction/DWPCAClaim/QualifyingBenefit/Answer
     * @return the newly created node
     */
    private Node add(String value, String xPath, boolean createEmptyNodes, Node localRootNode) {
        if(xPath == null || (createEmptyNodes == false && isValueEmpty(value))) {
            return null;
        }

        Node node = getNamedNode(xPath, null, false, localRootNode);
        if(isValueEmpty(value) == false) {
            add((String)value, node);
        }

        return node;
    }

    private void add(List<Map<String, String>> fieldCollectionList, String xPath) {
//        // create enclosing node, one per list item using order attribute, then create the inner values
//        if(fieldCollectionList == null) {
//            return;
//        }
//
//        for(int index = 0; index < fieldCollectionList.size(); index++) {
//            Map<String, String> fieldCollection = fieldCollectionList.get(index);
//            Node collectionNode = getNamedNode(xPath, addToAttrMap(null, "order", Integer.toString(index)), false, document);
//            this.add(fieldCollection, valueMappings, collectionNode);
//            Node textNode = document.createTextNode(value);
//            current.appendChild(textNode);
//        }
        throw new UnsupportedOperationException("Not implemented");
    }

    private List<Map<String, String>> castFieldCollectionList(Object untypedfieldCollection) {
        if((untypedfieldCollection instanceof List) == false) {
            throw new IllegalArgumentException("field collection list is not a 'List'");
        }
        List<?> list = (List<?>)untypedfieldCollection;
        for(Object item : list) {
            if(item != null && (item instanceof Map<?, ?>) == false) {
                throw new IllegalArgumentException("item in the field collection list is not a 'Map'");
            }
            Map<?, ?> map = (Map<?, ?>)item;
            for(Map.Entry<?, ?> entry: map.entrySet()) {
                if(entry.getKey() != null && (entry.getKey() instanceof String) == false) {
                    throw new IllegalArgumentException("key in map instance in field collection is not a String");
                }
                if(entry.getValue() != null && (entry.getValue() instanceof String) == false) {
                    throw new IllegalArgumentException("value in map instance in field collection is not a String");
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = (List<Map<String, String>>)list;

        return result;
    }

    private void add(String value, Node current) {
        Node textNode = document.createTextNode(value);
        current.appendChild(textNode);
    }

    private String subpath(String path, int start, int end) {
        if(path == null) {
            return null;
        }

        int startOffset = 0;
        int endOffset = -1;
        int searchStartIndex = 0;

        for(int index = 0; index < end; index++) {
            searchStartIndex = path.indexOf(PATH_SEPARATOR, searchStartIndex);
            if(searchStartIndex < 0) {
                throw new IndexOutOfBoundsException("index = " + index);
            } else {
                if((index + 1) == start) {
                    startOffset = searchStartIndex;
                }
                if((index + 1) == end) {
                    endOffset = searchStartIndex;
                    break;
                }
            }
        }

        String substring = path.substring(startOffset, endOffset);
        return substring;
    }

    /**
     * Return a pre-existing child to this specific node that matches the childName and attributes, or if
     * it does not exist: create a new child node if create = true, otherwise return null;
     *
     * @param localRootNode the rootNode used for xPath calculations
     * @return
     */
    private Node getNamedNode(String xPath, Map<String, String> attributes, boolean attrExactMatch, Node localRootNode) {
        String[] pathElements = xPath.split(PATH_SEPARATOR);
        Node current = localRootNode;
        for(int index = 0; index < pathElements.length; index++) {
            String element = pathElements[index];
            Node childNode = getNamedNode(current, element, true, attributes, attrExactMatch);
            if(childNode == null) {
                throw new IllegalStateException("Unable to create node(" + element + ") at: " + subpath(xPath, 0, index));
            }
            current = childNode;
        }

        return current;
    }

    /**
     * Return a pre-existing child to this specific node that matches the childName and attributes, or if
     * it does not exist: create a new child node if create = true, otherwise return null;
     * @return
     */
    private Node getNamedNode(Node node, String childName, boolean create, Map<String, String> attributes, boolean attrExactMatch) {
        if(node == null || childName == null) {
            return null;
        }

        NodeList children = node.getChildNodes();
        for(int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            String name = child.getNodeName();
            if(childName.equals(name)) {
                if(attributes != null || attrExactMatch) {
                    Map<String, String> childAttrMap = attrsToMap(child.getAttributes());
                    if(attrsMatch(childAttrMap, attributes, attrExactMatch) == false) {
                        break;
                    }
                }
                return child;
            }
        }

        if(create) {
            // we can use node.getOwnerDocument also
            Element childNode = document.createElement(childName);
            node.appendChild(childNode);
            if(attributes != null) {
                for(Map.Entry<String, String> attribute: attributes.entrySet()) {
                    childNode.setAttribute(attribute.getKey(), attribute.getValue());
                }
            }
            return childNode;
        }

        return null;
    }

    private boolean attrsMatch(Map<String, String> childAttrs, Map<String, String> attributes, boolean attrExactMatch) {
        if(attrExactMatch) {
            return childAttrs.equals(attributes);
        }

        for(Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if((childAttrs.containsKey(key) == false)
            || (Objects.equals(childAttrs.get(key), value) == false)) {
                return false;
            }
        }
        return true;

    }

    private Map<String, String> addToAttrMap(Map<String, String> map, String name, String value) {
        Parameters.validateMandatoryArgs(name, "name");
        if(map == null) {
            map = new HashMap<>();
        }
        map.put(name, value);
        return map;
    }

    private Map<String, String> attrsToMap(NamedNodeMap rawAttrMap) {
        if(rawAttrMap == null) {
            return null;
        }

        Map<String, String> map = new HashMap<>();
        for(int index = 0; index < rawAttrMap.getLength(); index++) {
            Node attr = rawAttrMap.item(index);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            map.put(name, value);
        }

        return map;
    }

    private boolean isValueEmpty(Object value) {
        if(value == null) {
            return true;
        }
        if(value instanceof String) {
            String string = (String)value;
            if(string.trim().equals("")) {
                return true;
            }
        }
        return false;
    }

    public String render() throws InstantiationException {
        return render(true, false);
    }

    public String render(boolean includeXmlDeclaration, boolean prettyPrint) throws InstantiationException {
        String xml = XmlPrettyPrinter.xmlToString(document, prettyPrint, includeXmlDeclaration);
        return xml;
    }
}