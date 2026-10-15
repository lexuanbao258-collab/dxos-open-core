package com.tricore.dxos.infrastructure.storage.minio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.core.data.ObjectStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "dxos.object-storage.provider", havingValue = "minio")
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioStorageConfiguration {
    @Bean(destroyMethod = "close")
    S3Client minioS3Client(MinioStorageProperties properties) { return createClient(properties); }

    public static S3Client createClient(MinioStorageProperties p) {
        return S3Client.builder().endpointOverride(p.endpoint()).region(Region.of(p.region()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey())))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(Apache5HttpClient.builder().connectionTimeout(p.connectTimeout())
                        .connectionAcquisitionTimeout(p.connectTimeout()).socketTimeout(p.readTimeout()))
                .overrideConfiguration(c -> c.apiCallTimeout(p.requestTimeout()).apiCallAttemptTimeout(p.requestTimeout())
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
    }
    @Bean
    ObjectStoragePort minioObjectStoragePort(MinioStorageProperties properties, S3Client minioS3Client, ObjectMapper mapper) {
        return new MinioObjectStorageAdapter(properties, minioS3Client, mapper);
    }
}
