package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.*;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.SSLSession;
import static org.assertj.core.api.Assertions.*;

class SupabaseStorageTest {
    @TempDir Path temp;
    final ObjectMapper mapper = new ObjectMapper();
    SupabaseStorageProperties properties() {
        return new SupabaseStorageProperties(URI.create("https://storage.invalid/storage/v1"), "test-key", "secret",
                "test-bucket", "test", 1024L, 4096, 200000, 16384, temp, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
    static void error(Runnable action, StorageErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(StorageException.class, e -> {
            assertThat(e.code()).isEqualTo(code); assertThat(e.getMessage()).isEqualTo(code.name());
            assertThat(e.getCause()).isNull(); assertThat(e.getSuppressed()).isEmpty(); assertThat(e.getStackTrace()).isEmpty();
        });
    }
    StoreObjectCommand command(String value, StoreMode mode) {
        byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new StoreObjectCommand(new ObjectMetadata(new ObjectKey(" Mixed/../Key?%2e "),
                "text/plain; charset=UTF-8", bytes.length, Map.of("Source", "á", "source", "")), new ByteArrayInputStream(bytes), mode);
    }
    @Test void keysRoundTripAllCodeUnitsAndTraversalWithoutCollisions() {
        var codec=new SupabaseObjectKeyCodec("test", 200000);
        List<String> values=new ArrayList<>(List.of("A", "a", " A ", "../x", ".."+(char)92+"x", "/absolute", "%2e%2e", "?x#y", "á", "á", "😀"));
        StringBuilder every=new StringBuilder(); for(int i=0;i<=65535;i++) every.append((char)i); values.add(every.toString());
        Set<String> encoded=new HashSet<>();
        for(String value:values) { String physical=codec.encode(new ObjectKey(value)); assertThat(encoded.add(physical)).isTrue();
            assertThat(codec.decode(physical).value()).isEqualTo(value); assertThat(physical.substring(8)).matches("[A-Za-z0-9_-]+"); }
        error(()->codec.encode(null),StorageErrorCode.INVALID_INPUT);
        error(()->codec.decode("../foreign"),StorageErrorCode.INVALID_INPUT);
        error(()->new SupabaseObjectKeyCodec("test", 10).encode(new ObjectKey("too long")),StorageErrorCode.INVALID_INPUT);
    }
    @Test void metadataRoundTripsCaseUnicodeEmptyAndIsolatedSurrogates() throws Exception {
        var codec=new SupabaseMetadataCodec(mapper,4096);
        Map<String,String> map=new HashMap<>(); map.put("Source","á"); map.put("source", ""); map.put("名", new String(new char[]{(char)0xd800,0}));
        var metadata=new ObjectMetadata(new ObjectKey("k"), " Text/Plain; charset=UTF-8 ",0,map);
        String encoded=codec.encode(metadata);
        var node=mapper.readTree(Base64.getDecoder().decode(encoded));
        assertThat(codec.decode(metadata.key(),0,node)).isEqualTo(metadata);
        error(()->new SupabaseMetadataCodec(mapper,10).encode(metadata),StorageErrorCode.INVALID_INPUT);
        error(()->codec.decode(metadata.key(),0,mapper.createObjectNode()),StorageErrorCode.UNAVAILABLE);
    }
    @Test void stagingReadsCurrentPositionNeverClosesAndCleansUp() throws Exception {
        boolean[] closed={false};
        var input=new ByteArrayInputStream("prefixhello".getBytes()) { @Override public void close(){closed[0]=true;} };
        input.skip(6);
        var cmd=new StoreObjectCommand(new ObjectMetadata(new ObjectKey("k"),"text/plain",5,Map.of()),input,StoreMode.OVERWRITE);
        Path staged;
        try(var upload=StagedObjectUpload.create(cmd,temp,1024)){staged=upload.file();assertThat(Files.readString(staged)).isEqualTo("hello");}
        assertThat(Files.exists(staged)).isFalse();assertThat(closed[0]).isFalse();assertThat(input.read()).isEqualTo(-1);
    }
    @Test void stagingShortLongAndZeroBytes() throws Exception {
        for(long length:new long[]{0,9}) { var cmd=new StoreObjectCommand(new ObjectMetadata(new ObjectKey("k"),"text/plain",length,Map.of()),new ByteArrayInputStream("hello".getBytes()),StoreMode.OVERWRITE);
            error(()->StagedObjectUpload.create(cmd,temp,1024),StorageErrorCode.INVALID_INPUT); }
        try(var upload=StagedObjectUpload.create(new StoreObjectCommand(new ObjectMetadata(new ObjectKey("k"),"text/plain",0,Map.of()),InputStream.nullInputStream(),StoreMode.CREATE_ONLY),temp,1024)){assertThat(Files.size(upload.file())).isZero();}
        try(var entries=Files.list(temp)){assertThat(entries.count()).isZero();}
    }
    @Test void stagingInputFailuresRemainInvalidAndCallerOwned() throws Exception {
        for(boolean runtime:new boolean[]{false,true}) {
            boolean[] closed={false}; InputStream broken=new InputStream(){public int read()throws IOException{if(runtime)throw new IllegalStateException("secret");throw new IOException("secret");}public void close(){closed[0]=true;}};
            var cmd=new StoreObjectCommand(new ObjectMetadata(new ObjectKey("k"),"text/plain",1,Map.of()),broken,StoreMode.OVERWRITE);
            error(()->StagedObjectUpload.create(cmd,temp,1024),StorageErrorCode.INVALID_INPUT); assertThat(closed[0]).isFalse();
        }
        try(var entries=Files.list(temp)){assertThat(entries.count()).isZero();}
    }
    @Test void excessiveStagingStillDrainsToEofWithoutFurtherDiskWrites() throws Exception {
        int[] reads = {0};
        boolean[] closed = {false};
        var input = new InputStream() {
            @Override public int read() { throw new AssertionError("bulk reads expected"); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (reads[0] >= 2) {
                    try (var files = Files.list(temp)) {
                        assertThat(Files.size(files.findFirst().orElseThrow())).isEqualTo(8);
                    }
                }
                if (reads[0]++ == 4) return -1;
                Arrays.fill(bytes, offset, offset + 8, (byte) 1);
                return 8;
            }
            @Override public void close() { closed[0] = true; }
        };
        var command = new StoreObjectCommand(new ObjectMetadata(new ObjectKey("k"), "text/plain", 8, Map.of()),
                input, StoreMode.CREATE_ONLY);
        error(() -> StagedObjectUpload.create(command, temp, 8), StorageErrorCode.INVALID_INPUT);
        assertThat(reads[0]).isEqualTo(5);
        assertThat(closed[0]).isFalse();
        try (var files = Files.list(temp)) { assertThat(files.count()).isZero(); }
    }
    @Test void readsKeepMetadataAndCloseIdempotently() throws Exception {
        var m=command("hello",StoreMode.CREATE_ONLY).metadata();int[] closes={0};
        var resource=new SupabaseObjectContent(m,new ByteArrayInputStream("hello".getBytes()){public void close(){closes[0]++;}});
        assertThat(resource.read()).isEqualTo('h'); resource.content().close();resource.close();
        assertThat(closes[0]).isEqualTo(1);assertThat(resource.metadata()).isEqualTo(m);
        error(resource::content,StorageErrorCode.INVALID_INPUT);error(resource::read,StorageErrorCode.INVALID_INPUT);
    }
    @Test void lazyReadAndCloseFaultsAreSanitizedAndReleased() {
        var m=command("x",StoreMode.CREATE_ONLY).metadata();int[] closes={0};
        var read=new SupabaseObjectContent(m,new InputStream(){public int read()throws IOException{throw new IOException("apikey secret endpoint");}public void close(){closes[0]++;}});
        error(read::read,StorageErrorCode.UNAVAILABLE);assertThat(closes[0]).isEqualTo(1);
        var close=new SupabaseObjectContent(m,new ByteArrayInputStream(new byte[]{1}){public void close()throws IOException{throw new IOException("secret");}});
        error(close::close,StorageErrorCode.UNAVAILABLE);close.close();error(close::read,StorageErrorCode.INVALID_INPUT);
    }
    @Test void truncatedAndExcessProviderBytesAreUnavailable() {
        var m=command("hello",StoreMode.CREATE_ONLY).metadata();
        var shortRead=new SupabaseObjectContent(m,new ByteArrayInputStream(new byte[]{1}));assertThat(shortRead.read()).isEqualTo(1);error(shortRead::read,StorageErrorCode.UNAVAILABLE);
        var longRead=new SupabaseObjectContent(m,new ByteArrayInputStream(new byte[6]));error(()->longRead.read(new byte[6],0,6),StorageErrorCode.UNAVAILABLE);
    }
    @Test void errorCodesAreDeterministicAndNeverExposeBodies() {
        Map<String,StorageErrorCode> cases=Map.of("KeyAlreadyExists",StorageErrorCode.CONFLICT,"NoSuchKey",StorageErrorCode.NOT_FOUND,"AccessDenied",StorageErrorCode.ACCESS_FAILURE,"InvalidKey",StorageErrorCode.INVALID_INPUT,"NoSuchBucket",StorageErrorCode.UNAVAILABLE,"LockTimeout",StorageErrorCode.UNAVAILABLE);
        for(var entry:cases.entrySet()){var body=mapper.createObjectNode().put("code",entry.getKey()).put("message","apikey secret endpoint");error(()->{throw SupabaseStorageErrorMapper.response(400,body,true);},entry.getValue());}
        error(()->{throw SupabaseStorageErrorMapper.response(409,mapper.createObjectNode(),false);},StorageErrorCode.UNAVAILABLE);
        error(()->{throw SupabaseStorageErrorMapper.response(403,null,false);},StorageErrorCode.ACCESS_FAILURE);
    }
    @Test void configurationDisabledDoesNotRegisterFakeAndEnabledMissingConfigFailsClosed() {
        var runner=new ApplicationContextRunner().withUserConfiguration(SupabaseStorageConfiguration.class).withBean(ObjectMapper.class,ObjectMapper::new);
        runner.run(c->assertThat(c).doesNotHaveBean(ObjectStoragePort.class));
        runner.withPropertyValues("dxos.object-storage.provider=supabase").run(c->assertThat(c).hasFailed());
        assertThat(properties().toString()).doesNotContain("test-key","storage.invalid");
    }
    @Test void configurationValidRegistersOneRealPort() {
        new ApplicationContextRunner().withUserConfiguration(SupabaseStorageConfiguration.class).withBean(ObjectMapper.class,ObjectMapper::new)
            .withPropertyValues("dxos.object-storage.provider=supabase", "dxos.object-storage.supabase.rest-base-url=https://storage.invalid/storage/v1",
                "dxos.object-storage.supabase.api-key=test-key","dxos.object-storage.supabase.api-key-mode=secret",
                "dxos.object-storage.supabase.bucket=test-bucket","dxos.object-storage.supabase.key-prefix=test",
                "dxos.object-storage.supabase.max-object-bytes=1024","dxos.object-storage.supabase.max-metadata-bytes=4096",
                "dxos.object-storage.supabase.max-key-bytes=1024","dxos.object-storage.supabase.max-response-bytes=16384",
                "dxos.object-storage.supabase.staging-directory="+temp,"dxos.object-storage.supabase.connect-timeout=2s","dxos.object-storage.supabase.request-timeout=5s")
            .run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(ObjectStoragePort.class);});
    }
    @Test void providerConflictCleansStagingAndNeverHead() throws Exception {
        List<HttpRequest> requests=new ArrayList<>();
        var client=new SupabaseRestStorageClient(properties(),mapper,(req,handler)->{requests.add(req);return response(req,409,mapper.createObjectNode().put("code","KeyAlreadyExists").put("message","secret").toString());});
        var adapter=new SupabaseObjectStorageAdapter(properties(),client,mapper);
        error(()->adapter.store(command("hello",StoreMode.CREATE_ONLY)),StorageErrorCode.CONFLICT);
        assertThat(requests).hasSize(1);assertThat(requests.getFirst().method()).isEqualTo("POST");assertThat(requests.getFirst().headers().firstValue("x-upsert")).contains("false");
        try(var entries=Files.list(temp)){assertThat(entries.count()).isZero();}
    }
    @Test void overwriteSendsCompleteEnvelopeAndExactLength() throws Exception {
        List<HttpRequest> requests=new ArrayList<>();
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(req,handler)->{requests.add(req);return response(req,200,"{}");}),mapper);
        var cmd=command("hello",StoreMode.OVERWRITE);assertThat(adapter.store(cmd)).isEqualTo(cmd.metadata());
        var req=requests.getFirst();assertThat(req.headers().firstValue("x-upsert")).contains("true");assertThat(req.bodyPublisher().orElseThrow().contentLength()).isEqualTo(5);
        var node=mapper.readTree(Base64.getDecoder().decode(req.headers().firstValue("x-metadata").orElseThrow()));
        assertThat(new SupabaseMetadataCodec(mapper,4096).decode(cmd.metadata().key(),5,node)).isEqualTo(cmd.metadata());
    }
    @Test void invalidUploadNeverMutatesNetwork() {
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{throw new AssertionError("network called");}),mapper);
        error(()->adapter.store(null),StorageErrorCode.INVALID_INPUT);
        var m=new ObjectMetadata(new ObjectKey("k"),"text/plain",2,Map.of());error(()->adapter.store(new StoreObjectCommand(m,new ByteArrayInputStream(new byte[]{1}),StoreMode.OVERWRITE)),StorageErrorCode.INVALID_INPUT);
        var bad=new ObjectMetadata(new ObjectKey("k"),"text/plain"+(char)13+(char)10+"X:bad",0,Map.of());error(()->adapter.store(new StoreObjectCommand(bad,InputStream.nullInputStream(),StoreMode.CREATE_ONLY)),StorageErrorCode.INVALID_INPUT);
    }
    @Test void missingDeleteAndTransportFailuresAreNormalized() {
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->response(r,404,mapper.createObjectNode().put("code","NoSuchKey").toString())),mapper);
        error(()->adapter.delete(new ObjectKey("missing")),StorageErrorCode.NOT_FOUND);
        var broken=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{throw new IOException("endpoint secret");}),mapper);
        error(()->broken.metadata(new ObjectKey("k")),StorageErrorCode.UNAVAILABLE);
        var malformedForbidden = new SupabaseObjectStorageAdapter(properties(), new SupabaseRestStorageClient(
                properties(), mapper, (r, handler) -> response(r, 403, "non-JSON provider response")), mapper);
        error(() -> malformedForbidden.delete(new ObjectKey("k")), StorageErrorCode.ACCESS_FAILURE);
    }
    @Test void readPinsInfoAndDownloadToResolvedVersionNeverCurrentFallback() throws Exception {
        var cmd=command("hello",StoreMode.CREATE_ONLY);String physical=new SupabaseObjectKeyCodec("test",200000).encode(cmd.metadata().key());
        String version=UUID.randomUUID().toString();var metadata=mapper.readTree(Base64.getDecoder().decode(new SupabaseMetadataCodec(mapper,4096).encode(cmd.metadata())));
        var info=mapper.createObjectNode().put("version",version).put("name",physical).put("bucket_id","test-bucket").put("size",5);info.set("metadata",metadata);
        List<HttpRequest> requests=new ArrayList<>();
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{requests.add(r);return response(r,200,r.uri().getPath().contains("/info/")?info.toString():"hello");}),mapper);
        try(var resource=adapter.read(cmd.metadata().key())){assertThat(resource.metadata()).isEqualTo(cmd.metadata());assertThat(resource.content().readAllBytes()).isEqualTo("hello".getBytes());}
        assertThat(requests).hasSize(3);assertThat(requests.get(0).uri().getQuery()).isNull();
        assertThat(requests.get(1).uri().getQuery()).isEqualTo("versionId="+version);assertThat(requests.get(2).uri().getQuery()).isEqualTo("versionId="+version);
    }
    @Test void versionMismatchFailsClosedBeforeDownload() throws Exception {
        var cmd=command("hello",StoreMode.CREATE_ONLY);
        String physical=new SupabaseObjectKeyCodec("test",200000).encode(cmd.metadata().key());
        String first=UUID.randomUUID().toString(), second=UUID.randomUUID().toString();
        var metadata=mapper.readTree(Base64.getDecoder().decode(new SupabaseMetadataCodec(mapper,4096).encode(cmd.metadata())));
        List<HttpRequest> requests=new ArrayList<>();
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{
            requests.add(r);var info=mapper.createObjectNode().put("version",r.uri().getQuery()==null?first:second).put("name",physical).put("bucket_id","test-bucket").put("size",5);info.set("metadata",metadata);return response(r,200,info.toString());
        }),mapper);
        error(()->adapter.read(cmd.metadata().key()),StorageErrorCode.UNAVAILABLE);assertThat(requests).hasSize(2);
    }
    @Test void vanishedPinnedVersionDoesNotRetryCurrentKey() throws Exception {
        var cmd=command("hello",StoreMode.CREATE_ONLY);String physical=new SupabaseObjectKeyCodec("test",200000).encode(cmd.metadata().key());
        var metadata=mapper.readTree(Base64.getDecoder().decode(new SupabaseMetadataCodec(mapper,4096).encode(cmd.metadata())));
        var info=mapper.createObjectNode().put("version",UUID.randomUUID().toString()).put("name",physical).put("bucket_id","test-bucket").put("size",5);info.set("metadata",metadata);
        List<HttpRequest> requests=new ArrayList<>();
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{requests.add(r);return r.uri().getQuery()==null?response(r,200,info.toString()):response(r,404,mapper.createObjectNode().put("code","NoSuchKey").toString());}),mapper);
        error(()->adapter.read(cmd.metadata().key()),StorageErrorCode.NOT_FOUND);assertThat(requests).hasSize(2);
    }
    @Test void malformedAndOversizedResponsesAreSanitized() {
        for(String body:List.of("provider secret invalid json", "x".repeat(20000))) {
            var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->response(r,200,body)),mapper);
            error(()->adapter.metadata(new ObjectKey("k")),StorageErrorCode.UNAVAILABLE);
        }
    }
    @Test void oversizedMetadataIsRejectedBeforeTransport() {
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{throw new AssertionError("network called");}),mapper);
        var m=new ObjectMetadata(new ObjectKey("k"),"text/plain",0,Map.of("huge","x".repeat(10000)));
        error(()->adapter.store(new StoreObjectCommand(m,InputStream.nullInputStream(),StoreMode.CREATE_ONLY)),StorageErrorCode.INVALID_INPUT);
    }
    @Test void configurationRejectsInsecureEndpointAndTraversalPrefix() {
        var p=properties();
        for(String endpoint:List.of("http://storage.invalid/storage/v1","https://user:password@storage.invalid/storage/v1","https://storage.invalid/storage/v1?secret=key")) {
            error(()->new SupabaseStorageProperties(URI.create(endpoint),p.apiKey(),p.apiKeyMode(),p.bucket(),p.keyPrefix(),p.maxObjectBytes(),p.maxMetadataBytes(),p.maxKeyBytes(),p.maxResponseBytes(),p.stagingDirectory(),p.connectTimeout(),p.requestTimeout()),StorageErrorCode.INVALID_INPUT);
        }
        error(()->new SupabaseStorageProperties(p.restBaseUrl(),p.apiKey(),p.apiKeyMode(),p.bucket(),"../unsafe",p.maxObjectBytes(),p.maxMetadataBytes(),p.maxKeyBytes(),p.maxResponseBytes(),p.stagingDirectory(),p.connectTimeout(),p.requestTimeout()),StorageErrorCode.INVALID_INPUT);
    }
    @Test void secretKeyModeUsesApiKeyWithoutUserAuthAndNoWriteRetry() {
        List<HttpRequest> requests=new ArrayList<>();
        var adapter=new SupabaseObjectStorageAdapter(properties(),new SupabaseRestStorageClient(properties(),mapper,(r,handler)->{requests.add(r);throw new IOException("lost acknowledgment secret");}),mapper);
        error(()->adapter.store(command("data",StoreMode.OVERWRITE)),StorageErrorCode.UNAVAILABLE);
        assertThat(requests).hasSize(1);assertThat(requests.getFirst().headers().firstValue("Authorization")).isEmpty();
    }
    @Test void runtimeProviderFailuresAndBulkReadArgumentsAreNormalized() {
        var read=new SupabaseObjectContent(command("x",StoreMode.CREATE_ONLY).metadata(),new InputStream(){public int read(){throw new IllegalStateException("secret");}});
        error(read::read,StorageErrorCode.UNAVAILABLE);
        var closed=new SupabaseObjectContent(command("x",StoreMode.CREATE_ONLY).metadata(),new ByteArrayInputStream(new byte[]{1}));closed.close();
        error(()->closed.readNBytes(0),StorageErrorCode.INVALID_INPUT);error(()->closed.skipNBytes(0),StorageErrorCode.INVALID_INPUT);
    }
    static HttpResponse<InputStream> response(HttpRequest request,int status,String body) {
        return new HttpResponse<>() {
            public int statusCode(){return status;} public HttpRequest request(){return request;}
            public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
            public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
            public InputStream body(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}
            public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
        };
    }
}
