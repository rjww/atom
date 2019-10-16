/*
    :: src/AtomLink.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Representation of a single Atom link field, for serialization with JAXB.
*/

package rjww.atom;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "link")
@XmlType(propOrder = {"type", "href", "rel"})
public class AtomLink implements Serializable {
    private String type;
    private String href;
    private String rel;

    public String getType() { return this.type; }
    public String getHref() { return this.href; }
    public String getRel()  { return this.rel;  }

    @XmlAttribute
    public void setType(String type) { this.type = type; }
    @XmlAttribute
    public void setHref(String href) { this.href = href; }
    @XmlAttribute
    public void setRel(String rel)   { this.rel = rel;   }
}
