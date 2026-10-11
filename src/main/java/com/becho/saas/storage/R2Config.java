package com.becho.saas.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Builds the S3-compatible client pointed at Cloudflare R2.
 *
 * <p>The bean is always created so the application boots in dev without R2
 * credentials. {@link R2StorageService} checks {@link R2Properties#isConfigured()}
 * before any upload and returns a clean 503 when R2 is not set up, so the
 * placeholder credentials below are never used for a real request.
 */
@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2Config {

    @Bean
    public S3Client r2S3Client(R2Properties props) {
        String accessKey = R2Properties.hasText(props.getAccessKeyId()) ? props.getAccessKeyId() : "unconfigured";
        String secretKey = R2Properties.hasText(props.getSecretAccessKey()) ? props.getSecretAccessKey() : "unconfigured";
        String region = R2Properties.hasText(props.getRegion()) ? props.getRegion() : "auto";
        // R2_ENDPOINT overrides the endpoint for any S3-compatible storage
        // (e.g. Supabase Storage). Otherwise the Cloudflare R2 URL is built
        // from the account ID.
        String endpoint = R2Properties.hasText(props.getEndpoint())
                ? props.getEndpoint()
                : "https://" + (R2Properties.hasText(props.getAccountId()) ? props.getAccountId() : "unconfigured")
                + ".r2.cloudflarestorage.com";
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(props.getEndpoint() != null && !props.getEndpoint().isBlank())
                .build();
    }
}
