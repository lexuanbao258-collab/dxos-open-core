package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Real-provider child JVM: credentials inherited through environment, never command arguments. */
public final class SupabaseProcessWorker {
    public static void main(String[] args) {
        try {
            var properties=SupabaseRealSupport.properties(args[1]);
            var mapper=new ObjectMapper();
            try(var client=new SupabaseRestStorageClient(properties,mapper)) {
                var port=new SupabaseObjectStorageAdapter(properties,client,mapper);
                var key=new ObjectKey(args[2]);
                if(args[0].equals("create")) {
                    System.out.println("READY");System.out.flush();
                    if(new BufferedReader(new InputStreamReader(System.in)).readLine()==null) throw new IllegalStateException();
                    try { port.store(SupabaseRealSupport.command(key,args[3],StoreMode.CREATE_ONLY));System.out.println("STORED"); }
                    catch(StorageException error){System.out.println(error.code().name());}
                } else if(args[0].equals("store")) {
                    port.store(SupabaseRealSupport.command(key,args[3],StoreMode.OVERWRITE));System.out.println("STORED");
                } else if(args[0].equals("verify")) {
                    var expected=SupabaseRealSupport.command(key,args[3],StoreMode.OVERWRITE);
                    if(!port.metadata(key).equals(expected.metadata()))throw new IllegalStateException();
                    try(var object=port.read(key)) { if(!java.util.Arrays.equals(object.content().readAllBytes(),args[3].getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new IllegalStateException(); }
                    System.out.println("VERIFIED");
                } else if(args[0].equals("delete")) { port.delete(key);System.out.println("DELETED"); }
                else if(args[0].equals("missing")) {
                    try { port.delete(key);throw new IllegalStateException(); }
                    catch(StorageException error){if(error.code()!=StorageErrorCode.NOT_FOUND)throw error;}
                    try { port.metadata(key);throw new IllegalStateException(); }
                    catch(StorageException error){if(error.code()!=StorageErrorCode.NOT_FOUND)throw error;}
                    try { port.read(key).close();throw new IllegalStateException(); }
                    catch(StorageException error){if(error.code()!=StorageErrorCode.NOT_FOUND)throw error;}
                    System.out.println("MISSING");
                } else throw new IllegalStateException();
            }
        } catch(Exception | AssertionError error){System.out.println("FAILED");System.exit(1);}
    }
}
