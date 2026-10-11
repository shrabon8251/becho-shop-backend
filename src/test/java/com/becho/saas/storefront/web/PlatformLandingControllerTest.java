package com.becho.saas.storefront.web;

import com.becho.saas.modules.platform.entity.PlatformLanding;
import com.becho.saas.modules.platform.repository.PlatformLandingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Landing CMS: public GET returns saved content or default;
 * PUT validates JSON shape.
 */
@ExtendWith(MockitoExtension.class)
class PlatformLandingControllerTest {

  @Mock PlatformLandingRepository repo;

  @Test
  void get_returnsSavedContent() {
    PlatformLanding row = new PlatformLanding();
    row.setContent("{\"hero\":{\"title\":\"Custom\"}}");
    when(repo.findById(1L)).thenReturn(Optional.of(row));
    var ctrl = new PlatformLandingController(repo);
    assertEquals("{\"hero\":{\"title\":\"Custom\"}}", ctrl.get());
  }

  @Test
  void get_returnsDefaultWhenEmpty() {
    when(repo.findById(1L)).thenReturn(Optional.empty());
    var ctrl = new PlatformLandingController(repo);
    String def = ctrl.get();
    assertTrue(def.contains("Launch your dream store"));
    assertTrue(def.contains("\"starter\":399"));
  }

  @Test
  void save_rejectsNonJson() {
    var ctrl = new PlatformLandingController(repo);
    assertThrows(IllegalArgumentException.class,
        () -> ctrl.save("not-json"));
    assertThrows(IllegalArgumentException.class,
        () -> ctrl.save(""));
    verify(repo, never()).save(any());
  }

  @Test
  void save_persistsValidJson() {
    when(repo.findById(1L)).thenReturn(Optional.empty());
    var ctrl = new PlatformLandingController(repo);
    String json = "{\"hero\":{\"title\":\"Hi\"}}";
    assertEquals(json, ctrl.save(json));
    verify(repo).save(argThat(r ->
        "{\"hero\":{\"title\":\"Hi\"}}".equals(r.getContent())));
  }
}
