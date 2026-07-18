package pro.eng.yui.oss.ksj2tool.osm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmWay {
    @JacksonXmlProperty(isAttribute = true)
    private long id;
    @JacksonXmlProperty(isAttribute = true)
    private int version;

    @JacksonXmlProperty(localName = "nd")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmWayNode> nodes = new ArrayList<>();

    @JacksonXmlProperty(localName = "center")
    private OsmCenter center;

    @JacksonXmlProperty(localName = "tag")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmTag> tags = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public List<OsmWayNode> getNodes() { return nodes; }
    public void setNodes(List<OsmWayNode> nodes) { this.nodes = nodes; }
    public List<OsmTag> getTags() { return tags; }
    public void setTags(List<OsmTag> tags) { this.tags = tags; }
    public OsmCenter getCenter() { return center; }
    public void setCenter(OsmCenter center) { this.center = center; }

    public Map<String, String> getTagMap() {
        return tags.stream().collect(Collectors.toMap(OsmTag::getK, OsmTag::getV, (v1, v2) -> v1));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OsmWayNode {
        @JacksonXmlProperty(isAttribute = true)
        private long ref;
        public long getRef() { return ref; }
        public void setRef(long ref) { this.ref = ref; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OsmCenter {
        @JacksonXmlProperty(isAttribute = true)
        private double lat;
        @JacksonXmlProperty(isAttribute = true)
        private double lon;
        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
        public double getLon() { return lon; }
        public void setLon(double lon) { this.lon = lon; }
    }
}
