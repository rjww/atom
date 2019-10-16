/*
    :: src/AtomAuthor.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Representation of a single Atom author, for serialization with JAXB.
*/

package rjww.atom;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "author")
@XmlType(propOrder = {"name", "email"})
public class AtomAuthor implements Serializable {
    private String name;
    private String email;

    public String getName()            { return this.name;   }
    public String getEmail()           { return this.email;  }

    @XmlElement
    public void setName(String name)   { this.name = name;   }
    @XmlElement
    public void setEmail(String email) { this.email = email; }
}
