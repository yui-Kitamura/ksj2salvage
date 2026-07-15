package pro.eng.yui.oss.ksj2salvage.osm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmRelation {
    @JacksonXmlProperty(isAttribute = true)
    private long id;

    @JacksonXmlProperty(localName = "tag")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OsmTag> tags = new ArrayList<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public List<OsmTag> getTags() { return tags; }
    public void setTags(List<OsmTag> tags) { this.tags = tags; }

    public Map<String, String> getTagMap() {
        return tags.stream().collect(Collectors.toMap(OsmTag::getK, OsmTag::getV, (v1, v2) -> v1));
    }
}
