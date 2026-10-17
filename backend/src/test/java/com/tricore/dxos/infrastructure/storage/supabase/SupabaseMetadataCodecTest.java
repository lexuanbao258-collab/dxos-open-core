package com.tricore.dxos.infrastructure.storage.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tricore.dxos.core.data.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupabaseMetadataCodecTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectMetadata metadata(String contentType, Map<String, String> custom) {
        return new ObjectMetadata(new ObjectKey("k"), contentType, 0, custom);
    }
    private void rejectedBeforeTransformation(ObjectMetadata metadata) {
        var observed = spy(new ObjectMapper());
        SupabaseStorageTest.error(() -> new SupabaseMetadataCodec(observed, 256).encode(metadata),
                StorageErrorCode.INVALID_INPUT);
        verifyNoInteractions(observed);
    }
    @Test void oversizedCustomValueIsRejectedBeforeBuildingEnvelope() {
        rejectedBeforeTransformation(metadata("text/plain", Map.of("name", "x".repeat(1024))));
    }
    @Test void oversizedCustomNameIsRejectedBeforeBuildingEnvelope() {
        rejectedBeforeTransformation(metadata("text/plain", Map.of("x".repeat(1024), "value")));
    }
    @Test void aggregateSizeIsCheckedBeforeSortingOrBuildingEnvelope() {
        Map<String, String> custom = new HashMap<>();
        for (int i = 0; i < 64; i++) custom.put("name" + i, "value");
        rejectedBeforeTransformation(metadata("text/plain", custom));
    }
    @Test void oversizedContentTypeIsRejectedBeforeBuildingEnvelope() {
        rejectedBeforeTransformation(metadata("text/" + "x".repeat(1024), Map.of()));
    }
    @Test void exactEncodedBoundaryPreservesCodeUnitsAndCanonicalOrdering() throws Exception {
        var original = metadata("text/plain", Map.of("Source", "", "source", "\u0000\ud800\udfff"));
        String encoded = new SupabaseMetadataCodec(mapper, 4096).encode(original);
        var exact = new SupabaseMetadataCodec(mapper, encoded.length());
        assertThat(exact.encode(original)).isEqualTo(encoded);
        assertThat(new SupabaseMetadataCodec(mapper, encoded.length() + 3).encode(original)).isEqualTo(encoded);
        assertThat(exact.decode(original.key(), 0, mapper.readTree(Base64.getDecoder().decode(encoded))))
                .isEqualTo(original);
        SupabaseStorageTest.error(() -> new SupabaseMetadataCodec(mapper, encoded.length() - 1).encode(original),
                StorageErrorCode.INVALID_INPUT);
    }
    @Test void emptyMapBoundaryIncludesEnvelopeOverhead() {
        var original = metadata("a/b", Map.of());
        int length = new SupabaseMetadataCodec(mapper, 4096).encode(original).length();
        assertThat(new SupabaseMetadataCodec(mapper, length).encode(original)).hasSize(length);
        SupabaseStorageTest.error(() -> new SupabaseMetadataCodec(mapper, length - 1).encode(original),
                StorageErrorCode.INVALID_INPUT);
    }
    @Test void serializerExpansionIsStillBoundedAndSanitized() {
        var original = metadata("text/plain", Map.of("name", "value"));
        int compactLength = new SupabaseMetadataCodec(mapper, 4096).encode(original).length();
        var pretty = new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        SupabaseStorageTest.error(() -> new SupabaseMetadataCodec(pretty, compactLength).encode(original),
                StorageErrorCode.INVALID_INPUT);
    }
    @Test void oversizedProviderTreeIsRejectedWithoutMaterializingSerializedBytes() throws Exception {
        var original = metadata("text/plain", Map.of());
        ObjectNode tree = (ObjectNode) mapper.readTree(Base64.getDecoder().decode(
                new SupabaseMetadataCodec(mapper, 4096).encode(original)));
        tree.put("unrecognized", "x".repeat(16384));
        var observed = spy(new ObjectMapper());
        SupabaseStorageTest.error(() -> new SupabaseMetadataCodec(observed, 256).decode(original.key(), 0, tree),
                StorageErrorCode.UNAVAILABLE);
        verify(observed, never()).writeValueAsBytes(any());
    }
    @Test void providerJsonByteBoundaryIsPreserved() throws Exception {
        var original = metadata("text/plain", Map.of("name", "value"));
        var tree = mapper.readTree(Base64.getDecoder().decode(new SupabaseMetadataCodec(mapper, 4096).encode(original)));
        int length = mapper.writeValueAsBytes(tree).length;
        assertThat(new SupabaseMetadataCodec(mapper, length).decode(original.key(), 0, tree)).isEqualTo(original);
        SupabaseStorageTest.error(() -> new SupabaseMetadataCodec(mapper, length - 1).decode(original.key(), 0, tree),
                StorageErrorCode.UNAVAILABLE);
    }
}
