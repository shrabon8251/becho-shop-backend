package com.becho.saas.storefront.web;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Meta endpoints: POST /meta/reset-demo (mock only), GET /meta/announcements.
 */
@RestController
@RequestMapping("/meta")
public class MetaController {

  @PostMapping("/reset-demo")
  public void resetDemo() {
    // Mock only — the real backend has no demo data to reset.
  }

  @GetMapping("/announcements")
  public List<Map<String, Object>> announcements() {
    // Published platform announcements, visible to all store owners.
    return List.of();
  }
}
