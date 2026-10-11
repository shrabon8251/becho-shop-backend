package com.becho.saas.storefront.web;

import com.becho.saas.common.ApiException;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import com.becho.saas.modules.wallet.entity.GiftCard;
import com.becho.saas.modules.wallet.repository.GiftCardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Frontend-compatible gift card endpoints: /stores/{storeId}/gift-cards[/{id}],
 * plus POST /stores/{storeId}/gift-cards/check {code} → {balance}.
 *
 * Security: the raw code is shown ONCE at issuance and never persisted —
 * only its SHA-256 hash is stored. Redemption/check hashes the presented code.
 */
@RestController
@RequestMapping("/stores/{storeId}/gift-cards")
public class StoreGiftCardController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GiftCardRepository giftCards;
    private final StoreRepository storeRepository;
    private final StoreScope scope;

    public StoreGiftCardController(GiftCardRepository giftCards,
                                   StoreRepository storeRepository, StoreScope scope) {
        this.giftCards = giftCards;
        this.storeRepository = storeRepository;
        this.scope = scope;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF','SUPPORT')")
    public List<Map<String, Object>> list(@PathVariable String storeId) {
        Long sid = scope.require(storeId);
        return giftCards.findByStoreIdOrderByCreatedAtDesc(sid)
                .stream().map(this::toFrontend).collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> create(@PathVariable String storeId,
                                      @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        GiftCard gc = new GiftCard();
        gc.setStore(storeRepository.findById(sid).orElseThrow(() -> ApiException.notFound("Store")));
        long initialMinor = body.get("initial") instanceof Number n ? n.longValue()
                : body.get("balance") instanceof Number b ? b.longValue() : 0;
        String rawCode = generateCode();
        gc.setCodeHash(sha256(rawCode));
        // BUG-43 fix: the old prefix was "BECHO-" + 2 random chars (only ~1k
        // possibilities — collided fast and looked predictable). The code format is
        // now BECHO-XXXXXXXX-XXXX, so the first 8 random chars are a true prefix
        // with 32^8 (~1 trillion) possibilities. Still fits VARCHAR(8), no migration.
        gc.setCodePrefix(rawCode.substring(6, 14));
        gc.setInitialMinor(initialMinor);
        gc.setBalanceMinor(initialMinor);
        gc.setStatus("active");
        if (body.get("issuedToEmail") != null) gc.setIssuedToEmail((String) body.get("issuedToEmail"));
        GiftCard saved = giftCards.save(gc);
        // Return the raw code ONCE — it is never persisted and cannot be recovered.
        Map<String, Object> m = toFrontend(saved);
        m.put("code", rawCode);
        return m;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> save(@PathVariable String storeId,
                                    @PathVariable String id,
                                    @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        if (StoreScope.isNewId(id)) return create(storeId, body);
        GiftCard gc = findInStore(sid, id);
        Object active = body.get("active");
        if (active instanceof Boolean b) gc.setStatus(b ? "active" : "disabled");
        return toFrontend(giftCards.save(gc));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void remove(@PathVariable String storeId, @PathVariable String id) {
        Long sid = scope.require(storeId);
        GiftCard gc = findInStore(sid, id);
        gc.setStatus("disabled");
        giftCards.save(gc);
    }

    /**
     * Balance check used at checkout: POST /stores/{storeId}/gift-cards/check {code}.
     * Returns {balance}. Invalid/expired/empty cards are rejected — the code is
     * only ever compared by hash, same as redemption inside orders.create.
     */
    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','STAFF')")
    public Map<String, Object> check(@PathVariable String storeId,
                                     @RequestBody Map<String, Object> body) {
        Long sid = scope.require(storeId);
        String code = body.get("code") == null ? "" : ((String) body.get("code")).trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GIFT_CARD_INVALID", "Gift card not found");
        }
        GiftCard gc = giftCards.findByStoreIdAndCodeHashAndStatus(sid, sha256(code), "active")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GIFT_CARD_INVALID", "Gift card not found"));
        if (gc.getExpiresAt() != null && LocalDateTime.now().isAfter(gc.getExpiresAt())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GIFT_CARD_INVALID", "Gift card not found");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("balance", gc.getBalanceMinor());
        return m;
    }

    private GiftCard findInStore(Long sid, String id) {
        return giftCards.findById(StoreScope.lid(id))
                .filter(g -> g.getStore() != null && sid.equals(g.getStore().getId()))
                .orElseThrow(() -> ApiException.notFound("GiftCard"));
    }

    private Map<String, Object> toFrontend(GiftCard gc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", gc.getId().toString());
        // Raw code is never stored; expose the prefix for support lookup.
        m.put("code", gc.getCodePrefix());
        m.put("initial", gc.getInitialMinor());
        m.put("balance", gc.getBalanceMinor());
        m.put("active", "active".equals(gc.getStatus()));
        m.put("createdAt", gc.getCreatedAt() == null ? null
                : gc.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        return m;
    }

    private static String generateCode() {
        // BUG-43 fix: e.g. BECHO-7K2Q9X4M-8D2A — 12 random chars from a 32-char
        // alphabet (no 0/O, 1/I confusion), drawn with SecureRandom. The first 8
        // random chars (positions 6-13) double as the display prefix stored in
        // code_prefix, so it stays a true prefix of the code.
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("BECHO-");
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        sb.append('-');
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
