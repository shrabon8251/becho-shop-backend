package com.becho.saas.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2 (S3-compatible) object storage settings.
 *
 * <p>Every value comes from an environment variable — nothing is hardcoded.
 * All fields are blank by default so the app still boots in dev without R2;
 * {@link R2StorageService} refuses uploads with a clean 503 until configured.
 *
 * <pre>
 *   R2_ACCOUNT_ID        Cloudflare account ID
 *   R2_ACCESS_KEY_ID     R2 API token's access key ID
 *   R2_SECRET_ACCESS_KEY R2 API token's secret access key
 *   R2_BUCKET            R2 bucket name (e.g. becho-uploads)
 *   R2_PUBLIC_URL        Public base URL, e.g. https://pub-xxxx.r2.dev
 *                        or a custom domain (https://cdn.becho.com)
 *   R2_ENDPOINT          Optional: full S3 endpoint override, e.g.
 *                        https://xyz.supabase.co/storage/v1/s3 for Supabase
 *                        Storage (S3-compatible, no card needed). When set,
 *                        R2_ACCOUNT_ID is not required.
 *   R2_REGION            Optional: signing region, default "auto" (R2).
 *                        Use "us-east-1" for Supabase.
 * </pre>
 */
@ConfigurationProperties(prefix = "becho.r2")
public class R2Properties {

    private String accountId = "";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String bucket = "";
    private String publicUrl = "";
    private String endpoint = "";
    private String region = "auto";

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    /** True only when every value needed for an upload is present. */
    public boolean isConfigured() {
        return hasText(accessKeyId)
                && hasText(secretAccessKey)
                && hasText(bucket)
                && hasText(publicUrl)
                && (hasText(endpoint) || hasText(accountId));
    }

    static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
