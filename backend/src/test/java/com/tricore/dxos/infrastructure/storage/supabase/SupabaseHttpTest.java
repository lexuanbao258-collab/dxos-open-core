package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Local simulator proves HTTP wiring, not hosted Supabase atomicity/durability. */
class SupabaseHttpTest {
    @TempDir Path temp;
    ObjectMapper mapper=new ObjectMapper();
    HttpServer server;
    ExecutorService executor;
    HttpClient http;
    SupabaseRestStorageClient client;
    SupabaseObjectStorageAdapter port;
    SupabaseStorageProperties properties;
    final Map<String,Stored> current=new ConcurrentHashMap<>();
    final Map<String,Stored> versions=new ConcurrentHashMap<>();
    final List<HttpRequest> requests=new CopyOnWriteArrayList<>();
    record Stored(String name,String version,byte[] bytes,JsonNode metadata){}
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        executor=Executors.newCachedThreadPool();server.setExecutor(executor);server.createContext("/",this::handle);server.start();
        http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        properties=new SupabaseStorageProperties(URI.create("https://storage.invalid/storage/v1"),"test-key","service-role","test-bucket","http-test",1024L,4096,1024,16384,temp,Duration.ofSeconds(2),Duration.ofSeconds(5));
        client=new SupabaseRestStorageClient(properties,mapper,request->{
            requests.add(request);
            URI target=URI.create("http://localhost:"+server.getAddress().getPort()+request.uri().getRawPath()+(request.uri().getRawQuery()==null?"":"?"+request.uri().getRawQuery()));
            var builder=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(5));
            request.headers().map().forEach((name,values)->values.forEach(v->builder.header(name,v)));
            return http.send(builder.method(request.method(),request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build(),HttpResponse.BodyHandlers.ofInputStream());
        });
        port=new SupabaseObjectStorageAdapter(properties,client,mapper);
    }
    @AfterEach void stop(){if(client!=null)client.close();if(server!=null)server.stop(0);if(http!=null)http.close();if(executor!=null)executor.shutdownNow();}
    StoreObjectCommand command(String value,StoreMode mode){return SupabaseRealSupport.command(new ObjectKey(" ../ Mixed/Key "),value,mode);}
    @Test void fullHttpCrudReplacesMapAndDeletesStrictly() throws Exception {
        var first=command("hello",StoreMode.CREATE_ONLY);port.store(first);
        assertThat(port.metadata(first.metadata().key())).isEqualTo(first.metadata());
        try(var read=port.read(first.metadata().key())){assertThat(read.content().readAllBytes()).isEqualTo("hello".getBytes());}
        SupabaseStorageTest.error(()->port.store(command("loser",StoreMode.CREATE_ONLY)),StorageErrorCode.CONFLICT);
        var replacement=new ObjectMetadata(first.metadata().key(),"application/octet-stream",1,Map.of());
        port.store(new StoreObjectCommand(replacement,new ByteArrayInputStream(new byte[]{42}),StoreMode.OVERWRITE));
        assertThat(port.metadata(first.metadata().key())).isEqualTo(replacement);
        port.delete(first.metadata().key());SupabaseStorageTest.error(()->port.delete(first.metadata().key()),StorageErrorCode.NOT_FOUND);
        SupabaseStorageTest.error(()->port.read(first.metadata().key()),StorageErrorCode.NOT_FOUND);
        assertThat(requests).allSatisfy(r->{assertThat(r.headers().firstValue("apikey")).contains("test-key");assertThat(r.headers().firstValue("Authorization")).contains("Bearer test-key");});
        try(var files=Files.list(temp)){assertThat(files.count()).isZero();}
    }
    @Test void pinnedReadAfterMutationKeepsOldMetadataAndBody() throws Exception {
        var original=command("old",StoreMode.CREATE_ONLY);port.store(original);
        try(var read=port.read(original.metadata().key())){
            port.store(command("new",StoreMode.OVERWRITE));port.delete(original.metadata().key());
            assertThat(read.metadata()).isEqualTo(original.metadata());assertThat(read.content().readAllBytes()).isEqualTo("old".getBytes());
        }
    }
    @Test void zeroLengthAndCallerOwnershipOnSuccessAndConflict() throws Exception {
        boolean[] closed={false};var key=new ObjectKey("zero");
        InputStream input=new ByteArrayInputStream(new byte[0]){public void close(){closed[0]=true;}};
        var command=new StoreObjectCommand(new ObjectMetadata(key,"application/octet-stream",0,Map.of()),input,StoreMode.CREATE_ONLY);
        port.store(command);assertThat(closed[0]).isFalse();
        try(var read=port.read(key)){assertThat(read.content().read()).isEqualTo(-1);}
        SupabaseStorageTest.error(()->port.store(command),StorageErrorCode.CONFLICT);assertThat(closed[0]).isFalse();
    }
    @Test void invalidLengthPreservesExistingObjectWithoutNetworkMutation() throws Exception {
        var original=command("original",StoreMode.CREATE_ONLY);port.store(original);int before=requests.size();
        var invalid=new StoreObjectCommand(new ObjectMetadata(original.metadata().key(),"text/plain",1,Map.of()),new ByteArrayInputStream("long".getBytes()),StoreMode.OVERWRITE);
        SupabaseStorageTest.error(()->port.store(invalid),StorageErrorCode.INVALID_INPUT);assertThat(requests).hasSize(before);
        assertThat(port.metadata(original.metadata().key())).isEqualTo(original.metadata());
    }
    @Test void serverFaultIsSanitizedAndStagingCleaned() throws Exception {
        server.removeContext("/");server.createContext("/",e->{e.getRequestBody().readAllBytes();respond(e,500,mapper.createObjectNode().put("message","endpoint apikey secret").toString().getBytes());});
        SupabaseStorageTest.error(()->port.store(command("failure",StoreMode.CREATE_ONLY)),StorageErrorCode.UNAVAILABLE);
        try(var files=Files.list(temp)){assertThat(files.count()).isZero();}
    }
    private synchronized void handle(HttpExchange exchange)throws IOException {
        String url=exchange.getRequestURI().getPath();String name=url.substring(url.indexOf("/test-bucket/")+13);
        String query=exchange.getRequestURI().getQuery();String version=query==null?null:query.substring("versionId=".length());
        if(exchange.getRequestMethod().equals("POST")){
            byte[] bytes=exchange.getRequestBody().readAllBytes();
            if(!"true".equals(exchange.getRequestHeaders().getFirst("x-upsert"))&&current.containsKey(name)){fault(exchange,409,"KeyAlreadyExists");return;}
            JsonNode metadata=mapper.readTree(Base64.getDecoder().decode(exchange.getRequestHeaders().getFirst("x-metadata")));
            var stored=new Stored(name,UUID.randomUUID().toString(),bytes,metadata);current.put(name,stored);versions.put(stored.version(),stored);respond(exchange,200,"{}".getBytes());return;
        }
        if(exchange.getRequestMethod().equals("DELETE")){
            if(current.remove(name)==null){fault(exchange,404,"NoSuchKey");return;}respond(exchange,200,"{}".getBytes());return;
        }
        Stored stored=version==null?current.get(name):versions.get(version);
        if(stored==null||!stored.name().equals(name)){fault(exchange,404,"NoSuchKey");return;}
        if(url.contains("/info/")){
            var node=mapper.createObjectNode().put("name",name).put("version",stored.version()).put("bucket_id","test-bucket").put("size",stored.bytes().length).put("etag",stored.version());node.set("metadata",stored.metadata());respond(exchange,200,node.toString().getBytes());
        }else{exchange.getResponseHeaders().set("etag",stored.version());respond(exchange,200,stored.bytes());}
    }
    private void fault(HttpExchange e,int status,String code)throws IOException {respond(e,status,mapper.createObjectNode().put("code",code).put("message","provider secret").toString().getBytes());}
    private static void respond(HttpExchange e,int status,byte[] bytes)throws IOException {
        e.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);try(var output=e.getResponseBody()){output.write(bytes);}e.close();
    }
}
