package com.patra.expr;

/**
 * Text matching semantics for TERM operations.
 *
 * <p>Defines how text values should be matched against field content in search queries.
 */
public enum TextMatch {
  /** Matches text as a complete phrase preserving word order. */
  PHRASE,

  /** Matches text exactly as specified without tokenization. */
  EXACT,

  /** Matches if any of the tokens in the text are found in the field. */
  ANY
}
