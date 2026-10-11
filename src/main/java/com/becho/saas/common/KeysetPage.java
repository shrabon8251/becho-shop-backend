package com.becho.saas.common;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Keyset (cursor) pagination — O(log n) regardless of page depth,
 * unlike OFFSET which scans and discards rows.
 */
public record KeysetPage<T>(List<T> items, String nextCursor, boolean hasMore) {

  public static final int DEFAULT_SIZE = 50;
  public static final int MAX_SIZE = 200;

  /** Cursor format: ISO datetime + "|" + id */
  public static Cursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) return null;
    String[] parts = cursor.split("\\|", 2);
    if (parts.length != 2) return null;
    try {
      return new Cursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
    } catch (Exception e) {
      return null;
    }
  }

  public static String encode(LocalDateTime createdAt, Long id) {
    return createdAt + "|" + id;
  }

  public record Cursor(LocalDateTime createdAt, Long id) {}
}
