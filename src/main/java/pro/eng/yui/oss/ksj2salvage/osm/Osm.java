package pro.eng.yui.oss.ksj2salvage.osm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "osm")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Osm {
    @JacksonXmlProperty(localName = "node")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmNode> nodes = new ArrayList<>();

    @JacksonXmlProperty(localName = "relation")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmRelation> relations = new ArrayList<>();

    public List<OsmNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<OsmNode> nodes) {
        this.nodes = nodes;
    }

    public List<OsmRelation> getRelations() {
        return relations;
    }

    public void setRelations(List<OsmRelation> relations) {
        this.relations = relations;
    }
}
