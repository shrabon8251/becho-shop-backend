package com.becho.saas.storefront.web;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo themes endpoint: returns all 13 themes with products and images.
 */
class PlatformDemoThemesControllerTest {

  @Test
  @SuppressWarnings("unchecked")
  void get_returnsAllThemes() throws Exception {
    var ctrl = new PlatformDemoThemesController();
    Map<String, Object> result = ctrl.get();
    List<Map<String, Object>> themes = (List<Map<String, Object>>) result.get("themes");
    assertEquals(13, themes.size());
    // Check first theme structure.
    Map<String, Object> first = themes.get(0);
    assertEquals("minimal", first.get("id"));
    assertNotNull(first.get("name"));
    assertNotNull(first.get("heroImage"));
    List<Map<String, Object>> products = (List<Map<String, Object>>) first.get("products");
    assertFalse(products.isEmpty());
    Map<String, Object> prod = products.get(0);
    assertNotNull(prod.get("id"));
    assertNotNull(prod.get("title"));
    assertNotNull(prod.get("price"));
    assertNotNull(prod.get("images"));
  }
}
