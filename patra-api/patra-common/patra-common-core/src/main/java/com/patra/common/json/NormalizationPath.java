package com.patra.common.json;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks the current path during JSON normalization for error reporting and field matching. */
final class NormalizationPath {
  private final Deque<String> tokens = new ArrayDeque<>();

  void pushField(String field) {
    tokens.addLast(field);
  }

  void pop() {
    if (!tokens.isEmpty()) {
      tokens.removeLast();
    }
  }

  void markArray() {
    if (tokens.isEmpty()) {
      tokens.addLast("[]");
    } else {
      String last = tokens.removeLast();
      tokens.addLast(last + "[]");
    }
  }

  void unmarkArray() {
    if (tokens.isEmpty()) {
      return;
    }
    String last = tokens.removeLast();
    if (last.endsWith("[]")) {
      String base = last.substring(0, last.length() - 2);
      if (!base.isEmpty()) {
        tokens.addLast(base);
      }
    } else {
      tokens.addLast(last);
    }
  }

  String asString(boolean includeRoot) {
    if (tokens.isEmpty()) {
      return includeRoot ? "$" : "";
    }
    String joined = String.join(".", tokens);
    return includeRoot ? "$." + joined : joined;
  }

  String leaf() {
    if (tokens.isEmpty()) {
      return "";
    }
    String last = tokens.peekLast();
    while (last != null && last.endsWith("[]")) {
      last = last.substring(0, last.length() - 2);
    }
    return last == null ? "" : last;
  }
}
