package com.becho.saas.storefront.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo theme preview data for the landing page.
 * GET /platform/demo-themes (public, rate-limited).
 *
 * <p>Returns per-theme preview data (products with Unsplash images,
 * hero image) so the frontend landing page doesn't need local demo data.
 * Data mirrors frontend src/lib/demoData.ts + demoPhotos.ts.
 */
@RestController
@RequestMapping("/platform/demo-themes")
public class PlatformDemoThemesController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Theme IDs in display order (matches frontend THEMES). */
  private static final List<String> THEME_ORDER = List.of(
      "minimal", "fashion", "bold", "craft",
      "gadget", "jewel", "kids", "beauty", "organic",
      "pharma", "furniture", "sport", "food");

  /** Themes without their own niche use the lifestyle catalog. */
  private static final Map<String, String> NICHE_FALLBACK = Map.of(
      "minimal", "lifestyle", "fashion", "fashion", "bold", "lifestyle", "craft", "lifestyle");

  private volatile Map<String, Object> cached;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> get() throws Exception {
    if (cached == null) {
      synchronized (this) {
        if (cached == null) cached = build();
      }
    }
    return cached;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> build() throws Exception {
    var resource = new ClassPathResource("demo-themes.json");
    String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    Map<String, Object> data = MAPPER.readValue(json, Map.class);
    Map<String, List<String>> photos = (Map<String, List<String>>) data.get("photos");
    Map<String, List<List<Object>>> products = (Map<String, List<List<Object>>>) data.get("products");

    List<Map<String, Object>> themes = new ArrayList<>();
    for (String themeId : THEME_ORDER) {
      String niche = NICHE_FALLBACK.getOrDefault(themeId, themeId);
      List<String> nichePhotos = photos.getOrDefault(niche, List.of());
      List<List<Object>> nicheProducts = products.get(niche);

      List<Map<String, Object>> prods = new ArrayList<>();
      if (nicheProducts != null) {
        for (int i = 0; i < Math.min(8, nicheProducts.size()); i++) {
          List<Object> p = nicheProducts.get(i);
          String name = String.valueOf(p.get(0));
          long priceUsd = ((Number) p.get(1)).longValue();
          // USD niches → BDT (matches frontend toBDT).
          long priceBdt = isUsdNiche(niche)
              ? Math.round((priceUsd * 110) / 10.0) * 10
              : priceUsd;
          List<String> images = new ArrayList<>();
          if (i < nichePhotos.size()) images.add(nichePhotos.get(i));
          if (i + 4 < nichePhotos.size()) images.add(nichePhotos.get(i + 4));
          Map<String, Object> prod = new LinkedHashMap<>();
          prod.put("id", themeId + "-p" + i);
          prod.put("title", name);
          prod.put("price", priceBdt);
          prod.put("compareAt", null);
          prod.put("images", images);
          prods.add(prod);
        }
      }
      Map<String, Object> theme = new LinkedHashMap<>();
      theme.put("id", themeId);
      theme.put("name", themeName(themeId));
      theme.put("products", prods);
      theme.put("heroImage", nichePhotos.isEmpty() ? null : nichePhotos.get(0));
      themes.add(theme);
    }
    return Map.of("themes", themes);
  }

  private static boolean isUsdNiche(String niche) {
    return switch (niche) {
      case "gadget", "jewel", "kids", "beauty", "organic", "pharma", "lifestyle" -> true;
      default -> false;
    };
  }

  private static String themeName(String id) {
    return switch (id) {
      case "minimal" -> "Minimal";
      case "fashion" -> "Fashion";
      case "bold" -> "Bold";
      case "craft" -> "Craft";
      case "gadget" -> "Gadget Dark";
      case "jewel" -> "Jewellery Luxury";
      case "kids" -> "Kids Playful";
      case "beauty" -> "Beauty Glow";
      case "organic" -> "Organic Fresh";
      case "pharma" -> "Pharmacy Care";
      case "furniture" -> "Furniture Warm";
      case "sport" -> "Sport Active";
      case "food" -> "Foodie Hub";
      default -> id;
    };
  }
}
