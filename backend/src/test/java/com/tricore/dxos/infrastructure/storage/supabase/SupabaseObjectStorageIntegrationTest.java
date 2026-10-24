package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Tag("supabase-real")
class SupabaseObjectStorageIntegrationTest {
    SupabaseStorageProperties properties;
    SupabaseRestStorageClient client;
    SupabaseObjectStorageAdapter port;
    ObjectKey key;
    @BeforeEach void setup() {
        properties=SupabaseRealSupport.properties(SupabaseRealSupport.required("SUPABASE_STORAGE_KEY_PREFIX")+"_it_"+UUID.randomUUID().toString().replace("-",""));
        assertThat(properties.bucket()).as("Use existing private dxos-objects bucket").isEqualTo("dxos-objects");
        client=new SupabaseRestStorageClient(properties,new ObjectMapper());port=new SupabaseObjectStorageAdapter(properties,client,new ObjectMapper());key=new ObjectKey("integration/ Mixed Key ");
    }
    @AfterEach void cleanup() {
        try { if(port!=null)try{port.delete(key);}catch(StorageException e){if(e.code()!=StorageErrorCode.NOT_FOUND)throw e;} }
        finally { if(client!=null)client.close(); }
    }
    @Test void createOnlyAndConflictPreserveWinner() throws Exception {
        var original=SupabaseRealSupport.command(key,"original",StoreMode.CREATE_ONLY);port.store(original);
        SupabaseStorageTest.error(()->port.store(SupabaseRealSupport.command(key,"loser",StoreMode.CREATE_ONLY)),StorageErrorCode.CONFLICT);
        assertObject("original");
    }
    @Test void overwriteReplacesContentTypeAndEntireCustomMap() throws Exception {
        port.store(SupabaseRealSupport.command(key,"old",StoreMode.CREATE_ONLY));
        var m=new ObjectMetadata(key,"application/octet-stream",1,Map.of());port.store(new StoreObjectCommand(m,new ByteArrayInputStream(new byte[]{42}),StoreMode.OVERWRITE));
        assertThat(port.metadata(key)).isEqualTo(m);
        try(var read=port.read(key)){assertThat(read.metadata()).isEqualTo(m);assertThat(read.content().readAllBytes()).containsExactly((byte)42);}
    }
    @Test void readMetadataAndRestEnvelopeRoundTrip() throws Exception {
        var command = SupabaseRealSupport.command(key, "Unicode á", StoreMode.CREATE_ONLY);
        String stage = "STORE";
        try {
            port.store(command);

            stage = "METADATA";
            assertThat(port.metadata(key)).isEqualTo(command.metadata());

            stage = "READ";
            try (var read = port.read(key)) {
                assertThat(read.metadata()).isEqualTo(command.metadata());

                stage = "DOWNLOAD CONTENT";
                assertThat(read.content().readAllBytes())
                        .isEqualTo("Unicode á".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (StorageException error) {
            // Temporary test-only diagnostics: retain the stage, never provider details or causes.
            throw new AssertionError("Supabase diagnostic stage=" + stage + "; storageCode=" + error.code());
        } catch (Exception | AssertionError error) {
            // Assertion messages can contain object content; do not propagate them to the report.
            throw new AssertionError("Supabase diagnostic stage=" + stage + "; operation or assertion failed");
        }
    }
    @Test void zeroByteObjectRoundTrips() throws Exception {port.store(SupabaseRealSupport.command(key,"",StoreMode.CREATE_ONLY));assertObject("");}
    @Test void deleteAndRepeatedDelete() {
        port.store(SupabaseRealSupport.command(key,"data",StoreMode.CREATE_ONLY));port.delete(key);
        SupabaseStorageTest.error(()->port.delete(key),StorageErrorCode.NOT_FOUND);
        SupabaseStorageTest.error(()->port.metadata(key),StorageErrorCode.NOT_FOUND);
        SupabaseStorageTest.error(()->port.read(key),StorageErrorCode.NOT_FOUND);
    }
    @Test void sameVersionReadSurvivesOverwriteAndDelete() throws Exception {
        String original = "A".repeat(65536);
        var command = SupabaseRealSupport.command(key, original, StoreMode.CREATE_ONLY,
                Map.of("Source", "á", "source", "", "payload", "snapshot-original"));
        versionStage("1 CREATE_ONLY store original object", () -> {
            // Numeric limits only: never print metadata, credentials, requests or provider bodies.
            System.out.println("Supabase snapshot fixture: objectBytes=" + command.metadata().contentLength()
                    + "; maxObjectBytes=" + properties.maxObjectBytes()
                    + "; maxMetadataBytes=" + properties.maxMetadataBytes());
            String envelope = new SupabaseMetadataCodec(new ObjectMapper(), properties.maxMetadataBytes())
                    .encode(command.metadata());
            System.out.println("Supabase snapshot fixture: encodedMetadataBytes=" + envelope.length());
            return port.store(command);
        });
        var read = versionStage("2 Open read handle", () -> port.read(key));
        // A separate close diagnostic preserves both the primary and suppressed close failures.
        try (AutoCloseable close = () -> versionStage("7 Close read handle", () -> {
            read.close();
            return null;
        })) {
            versionStage("3 OVERWRITE object", () ->
                    port.store(SupabaseRealSupport.command(key, "replacement", StoreMode.OVERWRITE,
                            Map.of("Source", "á", "source", "", "payload", "snapshot-replacement"))));
            versionStage("4 DELETE object", () -> { port.delete(key); return null; });
            versionStage("5 Access previously opened metadata", () -> {
                assertThat(read.metadata()).isEqualTo(command.metadata());
                return null;
            });
            versionStage("6 Read previously opened content", () -> {
                assertThat(read.content().readAllBytes())
                        .isEqualTo(original.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return null;
            });
        }
    }
    private static <T> T versionStage(String stage, Callable<T> action) {
        System.out.println("Supabase diagnostic stage=" + stage + "; started");
        try {
            T result = action.call();
            System.out.println("Supabase diagnostic stage=" + stage + "; completed");
            return result;
        } catch (StorageException error) {
            throw new AssertionError("Supabase diagnostic stage=" + stage + "; storageCode=" + error.code());
        } catch (Exception | AssertionError error) {
            // Discard the original message/cause: assertions and IO errors may contain secrets.
            throw new AssertionError("Supabase diagnostic stage=" + stage + "; operation or assertion failed");
        }
    }
    @Test void exactVersionInfoAndDownloadNeverReturnReplacement() throws Exception {
        port.store(SupabaseRealSupport.command(key,"old",StoreMode.CREATE_ONLY));
        String physical=new SupabaseObjectKeyCodec(properties.keyPrefix(),properties.maxKeyBytes()).encode(key);
        var old=client.info(physical,null);port.store(SupabaseRealSupport.command(key,"new",StoreMode.OVERWRITE));
        // A purged old version may be NOT_FOUND. Returning the new version is never acceptable.
        try {
            var pinned=client.info(physical,old.version());assertThat(pinned.version()).isEqualTo(old.version());
            try(var content=new SupabaseObjectContent(new SupabaseMetadataCodec(new ObjectMapper(),properties.maxMetadataBytes()).decode(key,pinned.size(),pinned.metadata()),client.download(physical,pinned))) {
                assertThat(content.content().readAllBytes()).isEqualTo("old".getBytes());
            }
        } catch(StorageException error) {assertThat(error.code()).isEqualTo(StorageErrorCode.NOT_FOUND);}
    }
    @RepeatedTest(10) void concurrentCreatorsAcrossIndependentJvmsHaveExactlyOneWinner() throws Exception {
        Process first=worker("create","first");Process second=worker("create","second");
        var executor=Executors.newFixedThreadPool(2);
        try {
            var a=new BufferedReader(new InputStreamReader(first.getInputStream()));var b=new BufferedReader(new InputStreamReader(second.getInputStream()));
            assertThat(executor.submit(a::readLine).get(60,TimeUnit.SECONDS)).isEqualTo("READY");
            assertThat(executor.submit(b::readLine).get(60,TimeUnit.SECONDS)).isEqualTo("READY");
            first.getOutputStream().write(10);first.getOutputStream().flush();second.getOutputStream().write(10);second.getOutputStream().flush();
            String ra=executor.submit(a::readLine).get(60,TimeUnit.SECONDS);String rb=executor.submit(b::readLine).get(60,TimeUnit.SECONDS);
            assertThat(List.of(ra,rb)).containsExactlyInAnyOrder("STORED","CONFLICT");
            assertObject(ra.equals("STORED")?"first":"second");
            SupabaseStorageTest.error(()->port.store(SupabaseRealSupport.command(key,"third",StoreMode.CREATE_ONLY)),StorageErrorCode.CONFLICT);
            assertObject(ra.equals("STORED")?"first":"second");
        } finally {first.destroyForcibly();second.destroyForcibly();executor.shutdownNow();}
    }
    @Test void durabilityAcrossFreshJvmRestarts() throws Exception {
        assertWorker("store","durable","STORED");assertWorker("verify","durable","VERIFIED");
        assertWorker("store","replacement","STORED");assertWorker("verify","replacement","VERIFIED");
        assertWorker("delete","unused","DELETED");assertWorker("missing","unused","MISSING");
    }
    @Test void bucketPrivateAndObjectCannotBeDownloadedAnonymously() throws Exception {
        port.store(SupabaseRealSupport.command(key,"private",StoreMode.CREATE_ONLY));
        try(var http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build()) {
            String base=properties.restBaseUrl().toString().replaceAll("/$","");
            var bucket=HttpRequest.newBuilder(java.net.URI.create(base+"/bucket/"+properties.bucket())).header("apikey",properties.apiKey()).timeout(properties.requestTimeout());
            if(properties.apiKeyMode().equals("service-role"))bucket.header("Authorization","Bearer "+properties.apiKey());
            var result=http.send(bucket.GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            assertThat(result.statusCode()).isEqualTo(200);
            try(var body=result.body()){assertThat(new ObjectMapper().readTree(body.readNBytes(properties.maxResponseBytes())).path("public").asBoolean(true)).isFalse();}
            String physical=new SupabaseObjectKeyCodec(properties.keyPrefix(),properties.maxKeyBytes()).encode(key);
            var anonymous=http.send(HttpRequest.newBuilder(java.net.URI.create(base+"/object/public/"+properties.bucket()+"/"+physical)).timeout(properties.requestTimeout()).GET().build(),HttpResponse.BodyHandlers.discarding());
            assertThat(anonymous.statusCode()).isIn(400,401,403,404);
        }
    }
    private void assertObject(String value) throws Exception {
        var expected=SupabaseRealSupport.command(key,value,StoreMode.OVERWRITE);assertThat(port.metadata(key)).isEqualTo(expected.metadata());
        try(var read=port.read(key)){assertThat(read.metadata()).isEqualTo(expected.metadata());assertThat(read.content().readAllBytes()).isEqualTo(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    private Process worker(String action,String value)throws IOException {
        String javaExecutable=SupabaseProcessWorker.javaExecutable().toString();
        String classpath=System.getProperty("supabase.test.classpath");if(classpath==null)throw new AssertionError("Run Gradle supabaseIntegrationTest task");
        return new ProcessBuilder(javaExecutable,"-cp",classpath,SupabaseProcessWorker.class.getName(),action,properties.keyPrefix(),key.value(),value).redirectErrorStream(true).start();
    }
    private void assertWorker(String action,String value,String expected)throws Exception {
        Process process=worker(action,value);
        try {assertThat(process.waitFor(60,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();assertThat(new String(process.getInputStream().readAllBytes()).strip()).isEqualTo(expected);}
        finally {process.destroyForcibly();}
    }
}
