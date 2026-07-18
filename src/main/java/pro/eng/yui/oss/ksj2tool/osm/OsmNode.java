package pro.eng.yui.oss.ksj2tool.osm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmNode {
    @JacksonXmlProperty(isAttribute = true)
    private long id;
    @JacksonXmlProperty(isAttribute = true)
    private double lat;
    @JacksonXmlProperty(isAttribute = true)
    private double lon;
    @JacksonXmlProperty(isAttribute = true)
    private int version;

    @JacksonXmlProperty(localName = "tag")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmTag> tags = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public List<OsmTag> getTags() { return tags; }
    public void setTags(List<OsmTag> tags) { this.tags = tags; }

    public Map<String, String> getTagMap() {
        return tags.stream().collect(Collectors.toMap(OsmTag::getK, OsmTag::getV, (v1, v2) -> v1));
    }
}
