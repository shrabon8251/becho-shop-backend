package com.becho.saas.modules.platform.service;

import com.becho.saas.modules.auth.service.MailService;
import com.becho.saas.modules.customers.repository.CustomerRepository;
import com.becho.saas.modules.platform.entity.Automation;
import com.becho.saas.modules.platform.entity.AutomationRun;
import com.becho.saas.modules.platform.repository.AutomationRepository;
import com.becho.saas.modules.platform.repository.AutomationRunRepository;
import com.becho.saas.modules.tenancy.entity.Store;
import com.becho.saas.modules.tenancy.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation engine (Bug #15): trigger → queue → delayed send.
 *
 * <p>Merchants define email flows (trigger + delayHours + subject/body).
 * {@link #fire} is called on trigger events (customer created, checkout
 * abandoned, winback scan) and queues one run per active automation.
 * The {@link #runDue} scheduled job sends emails whose delay has elapsed.
 */
@Service
public class AutomationService {

  private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AutomationRepository automations;
  private final AutomationRunRepository runs;
  private final StoreRepository stores;
  private final CustomerRepository customers;
  private final MailService mail;

  public AutomationService(AutomationRepository automations,
                           AutomationRunRepository runs,
                           StoreRepository stores,
                           CustomerRepository customers,
                           MailService mail) {
    this.automations = automations;
    this.runs = runs;
    this.stores = stores;
    this.customers = customers;
    this.mail = mail;
  }

  /**
   * Fires a trigger event: queues an email run for every active automation
   * with this trigger. Deduplicates per (automation, email, trigger).
   */
  @Transactional
  public void fire(Long storeId, String trigger, String email,
                   String name, Map<String, String> vars) {
    if (email == null || email.isBlank()) return;
    Store store = stores.findById(storeId).orElse(null);
    if (store == null) return;
    List<Automation> rules = automations.findByStoreIdAndTriggerAndActiveTrue(storeId, trigger);
    for (Automation a : rules) {
      if (runs.existsByAutomationIdAndRecipientEmailAndTriggerAndStatus(
          a.getId(), email.toLowerCase().trim(), trigger, "pending")) {
        continue; // already queued
      }
      AutomationRun run = new AutomationRun();
      run.setStore(store);
      run.setAutomation(a);
      run.setTrigger(trigger);
      run.setRecipientEmail(email.toLowerCase().trim());
      run.setRecipientName(name);
      try {
        Map<String, String> allVars = new HashMap<>(vars == null ? Map.of() : vars);
        allVars.putIfAbsent("name", name == null ? "" : name);
        allVars.putIfAbsent("store", store.getName() == null ? "" : store.getName());
        run.setVars(MAPPER.writeValueAsString(allVars));
      } catch (Exception e) {
        run.setVars("{}");
      }
      run.setScheduledFor(LocalDateTime.now().plusHours(Math.max(0, a.getDelayHours())));
      run.setStatus("pending");
      runs.save(run);
      log.info("Queued automation '{}' for {} (trigger={})", a.getName(), email, trigger);
    }
  }

  /** Daily: fire winback for customers with no orders in 60+ days. */
  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void winbackScan() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(60);
    for (Store store : stores.findAll()) {
      try {
        List<Long> customerIds = runs.findWinbackCandidates(store.getId(), cutoff);
        for (Long cid : customerIds) {
          var customer = customers.findById(cid).orElse(null);
          if (customer == null || customer.getEmail() == null) continue;
          String name = ((customer.getFirstName() != null ? customer.getFirstName() : "")
              + " " + (customer.getLastName() != null ? customer.getLastName() : "")).trim();
          fire(store.getId(), "winback", customer.getEmail(), name, Map.of());
        }
      } catch (Exception e) {
        log.warn("Winback scan failed for store {}: {}", store.getId(), e.getMessage());
      }
    }
  }

  /** Every 15 minutes: send due automation emails. */
  @Scheduled(fixedDelay = 15 * 60 * 1000)
  @Transactional
  public void runDue() {
    List<AutomationRun> due = runs.findDue(LocalDateTime.now());
    for (AutomationRun run : due) {
      try {
        send(run);
      } catch (Exception e) {
        log.warn("Automation run {} failed: {}", run.getId(), e.getMessage());
        run.setStatus("failed");
        runs.save(run);
      }
    }
  }

  private void send(AutomationRun run) {
    Automation a = run.getAutomation();
    Store store = run.getStore();
    Map<String, String> vars = readVars(run.getVars());
    String subject = render(a.getSubject(), vars);
    String body = render(a.getBody(), vars);
    // Merchant's own Brevo account (no platform fallback).
    String sender = mail.resolveSender(store.getSettings());
    mail.sendTemplated(run.getRecipientEmail(), subject, body, sender, store.getSettings());
    run.setStatus("sent");
    run.setSentAt(LocalDateTime.now());
    runs.save(run);
    log.info("Sent automation '{}' to {}", a.getName(), run.getRecipientEmail());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> readVars(String json) {
    try {
      if (json == null || json.isBlank()) return Map.of();
      return MAPPER.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }

  /** {{var}} substitution (same syntax as NotificationService). */
  static String render(String tpl, Map<String, String> vars) {
    if (tpl == null) return "";
    String out = tpl;
    for (Map.Entry<String, String> e : vars.entrySet()) {
      out = out.replace("{{" + e.getKey() + "}}",
          e.getValue() == null ? "" : e.getValue());
    }
    return out;
  }
}
