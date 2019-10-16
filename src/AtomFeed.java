/*
    :: src/AtomFeed.java

    Author   Robert Woods <hi@robertwoods.me>
    Source   https://github.com/rjww/atom

    Representation of a single Atom feed, for serialization with JAXB.
*/

package rjww.atom;

import java.io.BufferedReader;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "feed")
@XmlType(propOrder = {"title", "subtitle", "link", "updated", "author", "id", "entries"})
public class AtomFeed implements Serializable {
    private String               title;
    private String               subtitle;
    private AtomLink             link;
    private String               updated;
    private AtomAuthor           author;
    private String               id;
    private ArrayList<AtomEntry> entries;

    public String               getTitle()    { return this.title;    }
    public String               getSubtitle() { return this.subtitle; }
    public AtomLink             getLink()     { return this.link;     }
    public String               getUpdated()  { return this.updated;  }
    public AtomAuthor           getAuthor()   { return this.author;   }
    public String               getId()       { return this.id;       }
    public ArrayList<AtomEntry> getEntries()  { return this.entries;  }

    @XmlElement
    public void setTitle(String title)       { this.title = title;       }
    @XmlElement
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    @XmlElement
    public void setLink(AtomLink link)       { this.link = link;         }
    @XmlElement
    public void setUpdated(String updated)   { this.updated = updated;   }
    @XmlElement
    public void setAuthor(AtomAuthor author) { this.author = author;     }
    @XmlElement
    public void setId(String id)             { this.id = id;             }
    @XmlElement(name = "entry")
    public void setEntries(ArrayList<AtomEntry> entries)
                                             { this.entries = entries;   }

    public AtomFeed() {
        this.entries = new ArrayList<AtomEntry>();
    }

    public AtomFeed(File inputFile) {
        this();
        readFromFile(inputFile);
    }

    public void addEntry(AtomEntry entry) {
        this.entries.add(entry);
    }

    public void clearEntries() {
        this.entries.clear();
    }

    // Parse the contents of the feed from a supplied input file, which has the
    // format specified in the assignment description.
    private void readFromFile(File inputFile) {
        try (BufferedReader in = Common.getBufferedReader(inputFile)) {
            String line;

            while ((line = in.readLine()) != null && !line.equals("entry")) {
                String[] tokens = line.split(":", 2);
                String key = tokens[0];
                String value = tokens[1];

                if (key.equals("title"))    this.setTitle(value);
                if (key.equals("subtitle")) this.setSubtitle(value);
                if (key.equals("updated"))  this.setUpdated(value);
                if (key.equals("id"))       this.setId(value);

                if (key.equals("author")) {
                    AtomAuthor author = new AtomAuthor();
                    author.setName(value);
                    this.setAuthor(author);
                }

                if (key.equals("link")) {
                    AtomLink link = new AtomLink();
                    link.setHref(value);
                    this.setLink(link);
                }
            }

            while (in.ready()) {
                AtomEntry entry = new AtomEntry();

                while ((line = in.readLine()) != null && !line.equals("entry")) {
                    String[] tokens = line.split(":", 2);
                    String key = tokens[0];
                    String value = tokens[1];

                    if (key.equals("title"))   entry.setTitle(value);
                    if (key.equals("updated")) entry.setUpdated(value);
                    if (key.equals("id"))      entry.setId(value);
                    if (key.equals("summary")) entry.setSummary(value);

                    if (key.equals("author")) {
                        AtomAuthor author = new AtomAuthor();
                        author.setName(value);
                        entry.setAuthor(author);
                    }

                    if (key.equals("link")) {
                        AtomLink link = new AtomLink();
                        link.setHref(value);
                        entry.setLink(link);
                    }
                }

                this.addEntry(entry);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
