/*
    :: src/AtomEntry.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Representation of a single Atom feed entry, for serialization with JAXB.
*/

package rjww.atom;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "entry")
@XmlType(propOrder = {"title", "link", "id", "updated", "author", "summary"})
public class AtomEntry implements Serializable {
    private String     title;
    private AtomLink   link;
    private String     updated;
    private AtomAuthor author;
    private String     id;
    private String     summary;

    public String     getTitle()    { return this.title;   }
    public AtomLink   getLink()     { return this.link;    }
    public String     getUpdated()  { return this.updated; }
    public AtomAuthor getAuthor()   { return this.author;  }
    public String     getId()       { return this.id;      }
    public String     getSummary()  { return this.summary; }

    @XmlElement
    public void setTitle(String title)       { this.title = title;     }
    @XmlElement
    public void setLink(AtomLink link)       { this.link = link;       }
    @XmlElement
    public void setUpdated(String updated)   { this.updated = updated; }
    @XmlElement
    public void setAuthor(AtomAuthor author) { this.author = author;   }
    @XmlElement
    public void setId(String id)             { this.id = id;           }
    @XmlElement
    public void setSummary(String summary)   { this.summary = summary; }
}
