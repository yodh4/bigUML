package com.borkdominik.big.glsp.uml.converter.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public final class XmlDocumentService {
    public Document read(Path input) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream stream = Files.newInputStream(input)) {
                return builder.parse(stream);
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse XML: " + e.getMessage(), e);
        }
    }

    public void write(Document document, Path output) throws IOException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "ASCII");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            try (OutputStream stream = Files.newOutputStream(output)) {
                transformer.transform(new DOMSource(document), new StreamResult(stream));
            }
        } catch (TransformerException e) {
            throw new IOException("Failed to write XML: " + e.getMessage(), e);
        }
    }
}
