package es.josxtm.telecontrol.sync;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;

public final class SafFolder implements ExchangeFolder {
    private final ContentResolver resolver;private final Uri tree,root;
    private static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
    public SafFolder(Context c,Uri uri){resolver=c.getContentResolver();tree=uri;root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
    public static boolean granted(Context c,Uri tree){if(tree==null)return false;for(UriPermission p:c.getContentResolver().getPersistedUriPermissions())if(p.getUri().equals(tree)&&p.isReadPermission()&&p.isWritePermission())return true;return false;}
    public String directoryName()throws Exception{try(Cursor c=resolver.query(root,new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c==null||!c.moveToFirst()||!DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(0)))throw new IOException("Document tree unavailable");return c.getString(1);}}
    @Override public List<Entry> list()throws Exception{
        List<Entry> rows=new ArrayList<>();Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor c=resolver.query(children,COLUMNS,null,null,null)){if(c==null)throw new IOException("Folder unavailable");while(c.moveToNext()){if(rows.size()>=100000)throw new IOException("Folder entry limit");rows.add(new Entry(DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)).toString(),c.getString(1),c.isNull(2)?-1:c.getLong(2),c.isNull(3)?0:c.getLong(3)));}}
        return rows;
    }
    @Override public byte[] read(Entry entry,int limit)throws Exception{
        try(InputStream input=resolver.openInputStream(Uri.parse(entry.key));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(input==null)throw new IOException("Document unavailable");byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Exchange file limit");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    @Override public Entry create(String name)throws Exception{
        Uri document=DocumentsContract.createDocument(resolver,root,"application/octet-stream",name);if(document==null)throw new IOException("Cannot create exchange file");
        try(Cursor c=resolver.query(document,COLUMNS,null,null,null)){if(c==null||!c.moveToFirst()||!name.equals(c.getString(1)))throw new IOException("Provider changed publication name");return new Entry(document.toString(),name,c.isNull(2)?-1:c.getLong(2),c.isNull(3)?0:c.getLong(3));}
    }
    @Override public void write(Entry entry,byte[] bytes)throws Exception{
        // Called ONLY for a freshly created document, never to replace another file.
        try(OutputStream output=resolver.openOutputStream(Uri.parse(entry.key),"w")){if(output==null)throw new IOException("Cannot write exchange file");output.write(bytes);output.flush();}
    }
}
