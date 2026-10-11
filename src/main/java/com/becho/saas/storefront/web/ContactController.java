package com.becho.saas.storefront.web;

import com.becho.saas.modules.auth.service.MailService;
import com.becho.saas.modules.platform.entity.ContactMessage;
import com.becho.saas.modules.platform.repository.ContactMessageRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public contact form: POST /contact.
 * No authentication required (it's the marketing site's contact page).
 * Messages are persisted and a notification email is attempted (best-effort).
 */
@RestController
@RequestMapping("/contact")
public class ContactController {

  private final ContactMessageRepository messages;
  private final MailService mail;
  private final String notifyTo;

  public ContactController(ContactMessageRepository messages, MailService mail,
                           @Value("${becho.contact.notify-to:}") String notifyTo) {
    this.messages = messages;
    this.mail = mail;
    this.notifyTo = notifyTo == null ? "" : notifyTo.trim();
  }

  public record ContactRequest(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Email @Size(max = 255) String email,
      @Size(max = 255) String subject,
      @NotBlank @Size(max = 5000) String message) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> submit(@Valid @RequestBody ContactRequest req) {
    ContactMessage m = new ContactMessage();
    m.setName(req.name().trim());
    m.setEmail(req.email().toLowerCase().trim());
    m.setSubject(req.subject() != null ? req.subject().trim() : null);
    m.setMessage(req.message().trim());
    messages.save(m);

    if (!notifyTo.isEmpty()) {
      mail.sendContactNotification(notifyTo,
          "[Becho Contact] " + (m.getSubject() != null ? m.getSubject() : "(no subject)"),
          "From: " + m.getName() + " <" + m.getEmail() + ">\n\n" + m.getMessage());
    }
    return Map.of("id", m.getId(), "received", true);
  }
}
