package com.becho.saas.seed;

import com.becho.saas.modules.catalog.entity.Product;
import com.becho.saas.modules.catalog.entity.ProductImage;
import com.becho.saas.modules.catalog.entity.ProductVariant;
import com.becho.saas.modules.catalog.repository.ProductRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Opt-in demo product seeder (Md's request, 2026-10-10).
 *
 * <p>Enabled ONLY when {@code BECHO_SEED_DEMO=true} (default: off — the bean does
 * not even load otherwise). On boot it inserts 8 demo products with real
 * Unsplash photos and BDT prices (minor units, matching the round-5 money
 * boundary: backend stores minor, frontend displays taka) into the target
 * store — {@code BECHO_SEED_DEMO_STORE_ID}, or the oldest store when blank.
 *
 * <p>Idempotent: if the store already has any active product, seeding is
 * skipped. Demo slugs are prefixed {@code demo-} so they never collide with
 * real products. Never runs in production unless someone explicitly opts in.
 */
@Component
@ConditionalOnProperty(name = "becho.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  /** title, slug suffix, BDT price (taka), Unsplash photo id. */
  private static final List<String[]> DEMO_PRODUCTS = List.of(
      new String[]{"Everyday Tote", "everyday-tote", "3190", "photo-1523275335684-37898b6baf30"},
      new String[]{"Classic Sneakers", "classic-sneakers", "8690", "photo-1542291026-7eec264c27ff"},
      new String[]{"Ceramic Mug", "ceramic-mug", "1980", "photo-1553062407-98eeb64c6a62"},
      new String[]{"Denim Jacket", "denim-jacket", "13200", "photo-1572635196237-14b3f281503f"},
      new String[]{"Leather Wallet", "leather-wallet", "4950", "photo-1491553895911-0055eca6402d"},
      new String[]{"Cotton Tee", "cotton-tee", "2750", "photo-1503602642458-232111445657"},
      new String[]{"Desk Lamp", "desk-lamp", "6600", "photo-1524805444758-089113d48a6d"},
      new String[]{"Wool Scarf", "wool-scarf", "3850", "photo-1585386959984-a4155224a1ad"}
  );

  private static final String PHOTO_URL =
      "https://images.unsplash.com/%s?w=600&q=80&auto=format&fit=crop";

  private final ProductRepository products;
  private final StoreRepository stores;
  private final String seedStoreId;

  public DemoDataSeeder(ProductRepository products,
                        StoreRepository stores,
                        @Value("${becho.seed.demo-store-id:}") String seedStoreId) {
    this.products = products;
    this.stores = stores;
    this.seedStoreId = seedStoreId == null ? "" : seedStoreId.trim();
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Store store = resolveStore().orElse(null);
    if (store == null) {
      log.warn("[DemoDataSeeder] BECHO_SEED_DEMO=true but no store found — skipping (create a store first).");
      return;
    }
    long existing = products.countActive(store.getId());
    if (existing > 0) {
      log.info("[DemoDataSeeder] store {} already has {} active product(s) — skipping.", store.getId(), existing);
      return;
    }
    int n = 0;
    for (String[] d : DEMO_PRODUCTS) {
      products.save(buildProduct(store, d[0], d[1], Long.parseLong(d[2]), d[3]));
      n++;
    }
    log.info("[DemoDataSeeder] seeded {} demo products into store {} (slugs demo-*).", n, store.getId());
  }

  private Optional<Store> resolveStore() {
    if (!seedStoreId.isEmpty()) {
      try {
        return stores.findById(Long.parseLong(seedStoreId));
      } catch (NumberFormatException e) {
        log.warn("[DemoDataSeeder] BECHO_SEED_DEMO_STORE_ID='{}' is not a number — skipping.", seedStoreId);
        return Optional.empty();
      }
    }
    return stores.findAll(PageRequest.of(0, 1, Sort.by("id"))).stream().findFirst();
  }

  private Product buildProduct(Store store, String title, String slugSuffix, long priceBdt, String photoId) {
    Product p = new Product();
    p.setStore(store);
    p.setTitle(title);
    p.setSlug("demo-" + slugSuffix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
    p.setDescription("Demo product — replace with your own catalog before launch.");
    p.setStatus("active");
    p.setProductType("Demo");
    p.setVendor("Becho Demo");
    p.setTags("[\"demo\"]");
    p.setPublishedAt(LocalDateTime.now());

    ProductVariant v = new ProductVariant();
    v.setProduct(p);
    v.setStore(store);
    v.setSku("DEMO-" + slugSuffix.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", ""));
    v.setOption1("Default");
    v.setPriceMinor(priceBdt * 100L);
    v.setPosition(0);
    p.getVariants().add(v);

    ProductImage img = new ProductImage();
    img.setProduct(p);
    img.setUrl(String.format(PHOTO_URL, photoId));
    img.setAlt(title);
    img.setPosition(0);
    p.getImages().add(img);

    return p;
  }
}
