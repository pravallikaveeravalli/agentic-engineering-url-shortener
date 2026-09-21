package agentic.shortener.policy;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reads {@code groupId:artifactId} coordinates from {@code pom.xml}'s {@code <dependencies>} block. Task
 * T100, for {@code POL-DEP-001}/{@code POL-LIC-001}.
 *
 * <p>Deliberately does not read {@code <parent>} or {@code <plugins>} — those are build tooling, not
 * dependencies this project's own code links against, and {@code POL-DEP-001}'s own definition is scoped
 * to dependencies.
 */
final class PomDependencies {

    private PomDependencies() {
    }

    static List<String> readCoordinates(Path pomXml) {
        List<String> coordinates = new ArrayList<>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(Files.newInputStream(pomXml));
            NodeList dependencyNodes = doc.getElementsByTagName("dependency");
            for (int i = 0; i < dependencyNodes.getLength(); i++) {
                Element dependency = (Element) dependencyNodes.item(i);
                // Only direct children of <dependencies> — plugin <dependency> elements live under
                // <plugin><dependencies>, and getElementsByTagName is document-wide, so filter by parent.
                if (!"dependencies".equals(dependency.getParentNode().getNodeName())) {
                    continue;
                }
                String groupId = textOf(dependency, "groupId");
                String artifactId = textOf(dependency, "artifactId");
                if (groupId != null && artifactId != null) {
                    coordinates.add(groupId + ":" + artifactId);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse " + pomXml, e);
        }
        return coordinates;
    }

    private static String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getParentNode() == parent) {
                return nodes.item(i).getTextContent().strip();
            }
        }
        return null;
    }
}
