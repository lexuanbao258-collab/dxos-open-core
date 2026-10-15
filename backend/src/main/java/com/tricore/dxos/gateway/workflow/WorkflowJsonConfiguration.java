package com.tricore.dxos.gateway.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.gateway.workflow.dto.ActivateWorkflowInstanceRequest;
import com.tricore.dxos.gateway.workflow.dto.CreateWorkflowInstanceRequest;
import com.tricore.dxos.gateway.workflow.dto.ExecuteWorkflowTransitionRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class WorkflowJsonConfiguration implements WebMvcConfigurer {
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> candidate : converters) {
            if (candidate instanceof MappingJackson2HttpMessageConverter converter) {
                // Copy the configured mapper so other modules keep their JSON behavior.
                ObjectMapper strict = converter.getObjectMapper().copy()
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
                for (Class<?> input : List.of(CreateWorkflowInstanceRequest.class,
                        ActivateWorkflowInstanceRequest.class, ExecuteWorkflowTransitionRequest.class)) {
                    converter.registerObjectMappersForType(input, registrations ->
                            converter.getSupportedMediaTypes().forEach(mediaType -> registrations.put(mediaType, strict)));
                }
            }
        }
    }
}
