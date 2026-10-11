package com.becho.saas.modules.platform.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automation template rendering: {{var}} substitution.
 */
class AutomationServiceTest {

  @Test
  void render_substitutesVars() {
    String out = AutomationService.render(
        "Hi {{name}}, welcome to {{store}}!",
        Map.of("name", "Rahim", "store", "MyShop"));
    assertEquals("Hi Rahim, welcome to MyShop!", out);
  }

  @Test
  void render_missingVarLeftAsIs() {
    String out = AutomationService.render("Hi {{name}} {{unknown}}", Map.of("name", "A"));
    assertEquals("Hi A {{unknown}}", out);
  }

  @Test
  void render_nullTemplate() {
    assertEquals("", AutomationService.render(null, Map.of()));
  }
}
