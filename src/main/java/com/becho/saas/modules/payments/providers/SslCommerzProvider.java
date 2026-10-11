package com.becho.saas.modules.payments.providers;

import com.becho.saas.modules.payments.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** SSLCommerz init + IPN validation. Credentials from env, never code. */
@Component
public class SslCommerzProvider implements PaymentProvider {

    private final RestTemplate rest = new RestTemplate();
    private final String storeId;
    private final String storePassword;
    private final boolean sandbox;

    public SslCommerzProvider(
            @Value("${becho.payments.sslcommerz.store-id:}") String storeId,
            @Value("${becho.payments.sslcommerz.store-password:}") String storePassword,
            @Value("${becho.payments.sslcommerz.sandbox:true}") boolean sandbox) {
        this.storeId = storeId;
        this.storePassword = storePassword;
        this.sandbox = sandbox;
    }

    @Override
    public String key() { return "sslcommerz"; }

    private String base() {
        return sandbox ? "https://sandbox.sslcommerz.com" : "https://securepay.sslcommerz.com";
    }

    @Override
    @SuppressWarnings("unchecked")
    public InitResult initPayment(InitRequest req) {
        if (storeId == null || storeId.isBlank() || storePassword == null || storePassword.isBlank()) {
            throw new com.becho.saas.common.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "SSLCOMMERZ_NOT_CONFIGURED",
                    "SSLCommerz is not configured (set SSLCZ_STORE_ID / SSLCZ_STORE_PASSWORD)");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("store_id", storeId);
        form.add("store_passwd", storePassword);
        form.add("total_amount", String.valueOf(req.amountMinor() / 100.0));
        form.add("currency", req.currency());
        form.add("tran_id", "BECHO-" + req.orderId() + "-" + System.currentTimeMillis());
        form.add("success_url", req.successUrl());
        form.add("fail_url", req.failUrl());
        form.add("cancel_url", req.cancelUrl());
        form.add("cus_email", req.customerEmail());
        form.add("cus_phone", req.customerPhone() == null ? "" : req.customerPhone());
        form.add("product_category", "general");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var res = rest.postForEntity(base() + "/gwprocess/v4/api.php",
                new HttpEntity<>(form, headers), Map.class);
        Map<String, Object> body = res.getBody();
        if (body == null || !"SUCCESS".equals(body.get("status"))) {
            throw new IllegalStateException("SSLCommerz init failed: " + body);
        }
        return new InitResult((String) body.get("GatewayPageURL"),
                (String) body.get("sessionkey"), null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VerifyResult verifyCallback(CallbackPayload payload) {
        // Server-to-server validation call — never trust the browser callback alone.
        Map<String, String> p = payload.params();
        String valId = p.get("val_id");
        if (valId == null || valId.isBlank()) {
            return new VerifyResult(false, null, 0, null, null, "failed", "{}");
        }
        Map<String, Object> body;
        try {
            String url = base() + "/validator/api/validationserverAPI.php?val_id="
                    + URLEncoder.encode(valId, StandardCharsets.UTF_8)
                    + "&store_id=" + URLEncoder.encode(storeId, StandardCharsets.UTF_8)
                    + "&store_passwd=" + URLEncoder.encode(storePassword, StandardCharsets.UTF_8)
                    + "&v=1&format=json";
            body = rest.getForObject(url, Map.class);
        } catch (Exception e) {
            // Gateway unreachable — fail closed; the gateway will retry the IPN.
            return new VerifyResult(false, valId, 0, null, null, "failed", "{}");
        }
        boolean ok = body != null && "VALID".equals(body.get("status"));
        long amountMinor = ok
                ? Math.round(Double.parseDouble(String.valueOf(body.get("amount"))) * 100)
                : 0;
        String currency = ok && body.get("currency") != null ? String.valueOf(body.get("currency")) : null;
        String tranId = ok && body.get("tran_id") != null ? String.valueOf(body.get("tran_id")) : null;
        return new VerifyResult(ok, valId, amountMinor, currency, tranId,
                ok ? "success" : "failed", body == null ? "{}" : body.toString());
    }
}
