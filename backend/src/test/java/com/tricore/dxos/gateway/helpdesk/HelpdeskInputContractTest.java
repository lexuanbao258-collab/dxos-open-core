package com.tricore.dxos.gateway.helpdesk;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import com.tricore.dxos.infrastructure.workflow.HelpdeskService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class HelpdeskInputContractTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private final ObjectMapper json = new ObjectMapper();

    @AfterAll static void closeValidator() { FACTORY.close(); }

    static Stream<Arguments> inputs() {
        return Stream.of(
                Arguments.of(HelpdeskController.Create.class, "Create", "{\"title\":\"ok\",\"description\":\"ok\",\"requestType\":\"IT_HELPDESK\"}", List.of("title", "description", "requestType")),
                Arguments.of(HelpdeskController.Version.class, "Version", "{\"expectedVersion\":0}", List.of("expectedVersion")),
                Arguments.of(HelpdeskController.Assign.class, "Assign", "{\"assigneeId\":\"worker\",\"expectedAssignmentVersion\":0}", List.of("assigneeId", "expectedAssignmentVersion")),
                Arguments.of(HelpdeskController.Resolve.class, "Resolve", "{\"expectedVersion\":0,\"resolution\":\"ok\"}", List.of("expectedVersion", "resolution")));
    }

    @ParameterizedTest @MethodSource("inputs")
    void generatedSchemaRejectsUnknownFieldsAndKeepsActualRequiredFields(Class<?> type, String name, String valid, List<String> required) {
        Schema<?> schema = schema(type, name);
        assertThat(schema.getAdditionalProperties()).isEqualTo(false);
        assertThat(schema.getRequired()).containsExactlyInAnyOrderElementsOf(required);
    }

    @ParameterizedTest @MethodSource("inputs")
    void jacksonStillRejectsUnknownFieldsEvenWithItsGlobalUnknownPropertyCheckDisabled(Class<?> type, String name, String valid, List<String> required) throws Exception {
        var permissive = json.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThat(VALIDATOR.validate(permissive.readValue(valid, type))).isEmpty();
        String extra = valid.substring(0, valid.length() - 1) + ",\"unexpected\":true}";
        assertThatThrownBy(() -> permissive.readValue(extra, type)).isInstanceOf(JsonMappingException.class);
    }

    @Test void nonblankPatternMatchesTheResolvedValidatorIncludingItsUnicodeBehavior() {
        Schema<?> title = property(HelpdeskController.Create.class, "Create", "title");
        assertThat(title.getMinLength()).isEqualTo(1);
        assertThat(title.getMaxLength()).isEqualTo(200);
        assertThat(title.getPattern()).isNotBlank();
        var pattern = java.util.regex.Pattern.compile(title.getPattern());
        for (int ch = 0; ch <= Character.MAX_VALUE; ch++) {
            String value = String.valueOf((char) ch);
            boolean accepted = VALIDATOR.validate(new HelpdeskController.Create(value, "ok", "IT_HELPDESK")).isEmpty();
            assertThat(pattern.matcher(value).find()).as("U+%04X", ch).isEqualTo(accepted);
        }
        for (String value : List.of("", " \t\r\n", "  ok  ", "\u00a0", "\u2003", "\u3000", "\ud83d\ude00")) {
            boolean accepted = VALIDATOR.validate(new HelpdeskController.Create(value, "ok", "IT_HELPDESK")).isEmpty();
            assertThat(pattern.matcher(value).find()).as("value %s", value).isEqualTo(accepted);
        }
    }

    @Test void allNonblankFieldSchemasCarryTheActualSizeLimits() {
        assertText(HelpdeskController.Create.class, "Create", "title", 200);
        assertText(HelpdeskController.Create.class, "Create", "description", 4000);
        assertText(HelpdeskController.Assign.class, "Assign", "assigneeId", 1024);
        assertText(HelpdeskController.Resolve.class, "Resolve", "resolution", 4000);
        assertThat(VALIDATOR.validate(new HelpdeskController.Create("x".repeat(201), "ok", "IT_HELPDESK"))).isNotEmpty();
        assertThat(VALIDATOR.validate(new HelpdeskController.Create("ok", "x".repeat(4001), "IT_HELPDESK"))).isNotEmpty();
        assertThat(VALIDATOR.validate(new HelpdeskController.Assign("x".repeat(1025), 0L))).isNotEmpty();
        assertThat(VALIDATOR.validate(new HelpdeskController.Resolve(0L, "x".repeat(4001)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new HelpdeskController.Create(null, "ok", "IT_HELPDESK"))).isNotEmpty();
        assertThat(VALIDATOR.validate(new HelpdeskController.Create("ok", "ok", "OTHER"))).isNotEmpty();
    }

    @Test void exactVersionSchemasAndJacksonKeepTheActualLongDomain() throws Exception {
        for (var target : List.of(HelpdeskController.Version.class, HelpdeskController.Assign.class, HelpdeskController.Resolve.class)) {
            String name = target.getSimpleName();
            String field = target == HelpdeskController.Assign.class ? "expectedAssignmentVersion" : "expectedVersion";
            Schema<?> version = property(target, name, field);
            assertThat(version.getFormat()).isEqualTo("int64");
            assertThat(version.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(version.getMaximum()).isEqualByComparingTo(BigDecimal.valueOf(Long.MAX_VALUE));
            String other = target == HelpdeskController.Assign.class ? ",\"assigneeId\":\"worker\"" : target == HelpdeskController.Resolve.class ? ",\"resolution\":\"ok\"" : "";
            for (String number : List.of("0", Long.toString(Long.MAX_VALUE))) {
                Object value = json.readValue("{\"" + field + "\":" + number + other + "}", target);
                assertThat(VALIDATOR.validate(value)).isEmpty();
            }
            for (String number : List.of("\"0\"", "0.5", "9223372036854775808")) {
                assertThatThrownBy(() -> json.readValue("{\"" + field + "\":" + number + other + "}", target)).isInstanceOf(JsonMappingException.class);
            }
            for (String number : List.of("-1", "null")) {
                Object value = json.readValue("{\"" + field + "\":" + number + other + "}", target);
                assertThat(VALIDATOR.validate(value)).isNotEmpty();
            }
        }
    }

    private static void assertText(Class<?> type, String name, String field, int maximum) {
        Schema<?> value = property(type, name, field);
        assertThat(value.getMinLength()).isEqualTo(1);
        assertThat(value.getMaxLength()).isEqualTo(maximum);
        assertThat(value.getPattern()).isEqualTo(property(HelpdeskController.Create.class, "Create", "title").getPattern());
    }

    private static Schema<?> schema(Class<?> type, String name) {
        var api = new OpenAPI().components(new Components()).paths(new Paths());
        for (Class<?> input : List.of(HelpdeskController.Create.class, HelpdeskController.Version.class,
                HelpdeskController.Assign.class, HelpdeskController.Resolve.class, HelpdeskService.Page.class)) {
            ModelConverters.getInstance(true).resolveAsResolvedSchema(new AnnotatedType(input).resolveAsRef(true))
                    .referencedSchemas.forEach(api.getComponents()::addSchemas);
        }
        HelpdeskOpenApiConfiguration.documentContract(api);
        return api.getComponents().getSchemas().get(name);
    }

    private static Schema<?> property(Class<?> type, String name, String field) { return schema(type, name).getProperties().get(field); }
}
