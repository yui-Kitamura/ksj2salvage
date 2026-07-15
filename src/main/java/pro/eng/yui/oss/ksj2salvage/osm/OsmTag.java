package pro.eng.yui.oss.ksj2salvage.osm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OsmTag {
    @JacksonXmlProperty(isAttribute = true)
    private String k;
    @JacksonXmlProperty(isAttribute = true)
    private String v;

    public String getK() { return k; }
    public void setK(String k) { this.k = k; }
    public String getV() { return v; }
    public void setV(String v) { this.v = v; }
}
