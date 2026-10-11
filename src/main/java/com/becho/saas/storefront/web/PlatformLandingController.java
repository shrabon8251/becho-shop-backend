package com.becho.saas.storefront.web;

import com.becho.saas.modules.platform.entity.PlatformLanding;
import com.becho.saas.modules.platform.repository.PlatformLandingRepository;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Landing page CMS: GET /platform/landing (public, rate-limited),
 * PUT /platform/landing (super admin only).
 *
 * <p>Content is the LandingContent JSON shape (announcement, hero,
 * features, pricing, themes, testimonials, faq, cta). Stored as a
 * single row (id=1) in platform_landing. First GET with no saved
 * content returns the built-in default.
 */
@RestController
@RequestMapping("/platform/landing")
public class PlatformLandingController {

  private final PlatformLandingRepository landing;

  public PlatformLandingController(PlatformLandingRepository landing) {
    this.landing = landing;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public String get() {
    return landing.findById(1L)
        .map(PlatformLanding::getContent)
        .orElseGet(() -> DEFAULT_LANDING);
  }

  @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
              produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public String save(@RequestBody String content) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Content is required");
    }
    // Basic sanity: must be a JSON object.
    String trimmed = content.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
      throw new IllegalArgumentException("Content must be a JSON object");
    }
    PlatformLanding row = landing.findById(1L).orElseGet(PlatformLanding::new);
    // Ensure id=1 for the single row (BaseEntity id is auto-generated;
    // set explicitly via reflection-free path on first save).
    try {
      var idField = row.getClass().getSuperclass().getDeclaredField("id");
      idField.setAccessible(true);
      if (idField.get(row) == null) {
        idField.set(row, 1L);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to set landing id", e);
    }
    row.setContent(trimmed);
    landing.save(row);
    return trimmed;
  }

  /** Default LandingContent (mirrors frontend src/lib/landing.ts defaultLanding()). */
  private static final String DEFAULT_LANDING = """
      {"announcement":{"on":false,"text":"🎉 Eid offer: 20% off all plans with code EID20"},"""
      + """
      "hero":{"title":"Launch your dream store","titleAccent":"in minutes.","""
      + """
      "subtitle":"Becho gives you a beautiful storefront, a powerful admin, POS and marketing — everything you need to sell online and in person.","""
      + """
      "cta":"Start free trial","note":"14-day free trial · No credit card required · Cancel anytime"},"""
      + """
      "features":["""
      + """
      {"icon":"🎨","title":"Stunning storefronts","desc":"Designer-made themes, drag-and-drop sections, no code needed."},"""
      + """
      {"icon":"📦","title":"Orders & inventory","desc":"Manage orders, stock, and fulfillments from one dashboard."},"""
      + """
      {"icon":"💳","title":"bKash, Nagad & cards","desc":"SSLCommerz, COD and manual MFS payments built in."},"""
      + """
      {"icon":"🚚","title":"Courier integrations","desc":"Pathao, RedX, Steadfast and more — print labels in one click."},"""
      + """
      {"icon":"📊","title":"Analytics & reports","desc":"Know your best sellers, top customers and revenue trends."},"""
      + """
      {"icon":"📱","title":"POS included","desc":"Sell in-store with the built-in point of sale."}],"""
      + """
      "pricing":{"starter":399,"growth":999,"pro":1999,"note":"0% transaction fee on all plans"},"""
      + """
      "themes":{"visible":[],"featured":""},"""
      + """
      "testimonials":["""
      + """
      {"name":"Rahim Uddin","role":"Fashion store owner, Dhaka","text":"Ami 1 dinei dokan chalu korechi. Order management eto sohoj hobe vabini.","rating":5},"""
      + """
      {"name":"Nusrat Jahan","role":"Cosmetics seller, Chattogram","text":"bKash payment + courier integration — sob ek jaygay. Khub bhalo.","rating":5},"""
      + """
      {"name":"Tanvir Ahmed","role":"Electronics store, Sylhet","text":"Support team onek helpful. Theme gulo dekhte premium lage.","rating":4}],"""
      + """
      "faq":["""
      + """
      {"q":"Do I need to know how to code?","a":"Not at all. Pick a theme, drag sections into place, and publish. Developers can still extend everything via API."},"""
      + """
      {"q":"Which payments are supported?","a":"Cash on delivery, bKash/Nagad/Rocket (manual + via SSLCommerz), and cards/bank through SSLCommerz."},"""
      + """
      {"q":"Can I use my own domain?","a":"Yes — connect any domain from Settings → Domains on paid plans."},"""
      + """
      {"q":"Is there a free trial?","a":"Every plan starts with a 14-day free trial. No credit card required."}],"""
      + """
      "cta":{"title":"Ready to start selling?","subtitle":"Join thousands of merchants building their dream store with Becho.","button":"Start free trial"}}""";
}
