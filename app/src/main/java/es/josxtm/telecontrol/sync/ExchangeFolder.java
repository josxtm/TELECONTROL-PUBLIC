package es.josxtm.telecontrol.sync;

import java.util.List;

/** Document-tree transport. No filesystem path, database sharing, rename or network assumptions. */
public interface ExchangeFolder {
    final class Entry {
        public final String key,name;public final long size,modified;
        public Entry(String key,String name,long size,long modified){this.key=key;this.name=name;this.size=size;this.modified=modified;}
        public String stamp(){return name+":"+size+":"+modified;}
    }
    List<Entry> list()throws Exception;
    byte[] read(Entry entry,int limit)throws Exception;
    Entry create(String name)throws Exception;
    void write(Entry entry,byte[] bytes)throws Exception;
}
