package pro.eng.yui.oss.ksj2tool.osm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

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

    @JacksonXmlProperty(localName = "way")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmWay> ways = new ArrayList<>();

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

    public List<OsmWay> getWays() {
        return ways;
    }

    public void setWays(List<OsmWay> ways) {
        this.ways = ways;
    }
}
