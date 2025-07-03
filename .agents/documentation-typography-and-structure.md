# Documentation Typography & Structure

This guide adapts principles from **MIL-STD-961** and **Apple Human Interface Guidelines** for use
with AI agents, with a focus on improving Kotlin KDoc documentation.

## Goals
- Improve **text flow** and **line width** in KDoc blocks.
- Ensure **consistent formatting**, **clarity**, and **navigability**.
- Enable AI tools to reliably generate or refactor documentation based on structured patterns.

---

## 1. General Formatting Rules

### 1.1 Code Width
- **Maximum line width for code:** `100 characters`
- **Maximum line width for documentation:** `90 characters`

### 1.2 Font Style and Presentation
- Use **Markdown-style formatting** within KDoc where supported (e.g., `inline code`).
- Format code blocks with fences and language identifiers:
  ```kotlin
  fun example() {
      // Implementation
  }
  ```

### External links in `@see` tag

- The `@see` tag is for [referencing API elements](https://kotlinlang.org/docs/kotlin-doc.html#see-identifier).
- External links are [not officially supported](https://github.com/Kotlin/dokka/issues/518).
- External links CAN be added when migrating from Javadoc.
- Format is:
  ```kotlin
  /**
   * Documentation text.
   *
   * @see <a href="https://my.site.com/my-page.html">Link title</a>
   */
  ```

### 1.3 Line Wrapping
- Hard-wrap KDoc lines at **90 characters** to improve readability in side-by-side editors and diffs.
- **Provide** text flow **convenient for human readers**.
- **Prefer** starting new sentences or breaking after commands on new lines **over filling** the width.

---

## 2. Sectioning and Navigation

### 2.1 Structure of a KDoc Block
Use the following standard structure in multi-line KDoc blocks:

```kotlin
/**
 * [Short summary sentence.]
 *
 * [Detailed description. Wrap at 90 characters.]
 *
 * @param paramName [Description of the parameter.]
 * @return [Description of the return value.]
 * @throws SomeException [Reason for throwing.]
 */
```

> **Note:** Start `@param`, `@return`, and `@throws` descriptions with a **capital letter** and
> end with a **period**. This follows guidance from Apple, Microsoft, and military documentation
> (MIL-STD-961F), all of which encourage clarity and full-sentence formatting.
> While Java Javadoc traditionally uses lowercase fragments, Kotlin documentation benefits
> from full-sentence style for better readability and tooling support.

### 2.2 Headers and Subsections
- Do **not** use Markdown headers level 1 and level 2 (`#`, `##`) in KDoc.
- Use spacing and keywords (`@param`, `@return`, `@throws`) to denote sections.

### 2.3 Ordering for Navigability
Always order tags as follows for consistency:
1. `@param` (in declaration order)
2. `@return`
3. `@throws`
4. `@see` / `@sample`

---

## 3. Clarity and Language

### 3.1 Sentence Style
- Use **complete sentences** with a subject and verb.
- Start with a **capital letter**.
- End with a **period**, unless it's a list item or tag line.

### 3.2 Vocabulary and Tone
- Be **neutral**, **precise**, and **audience-aware**.
- Avoid unnecessary jargon.
- Favor active voice: “Initializes the engine,” not “The engine is initialized.”

### 3.3 Lists and Steps
Use hyphen lists (`-`) or numbered lists (`1.`, `2.`) inside KDoc only if supported by tooling.

```kotlin
/**
 * Loads a configuration:
 * - Reads from disk.
 * - Parses YAML.
 * - Caches the result.
 */
```

---

## 4. Punctuation and Consistency

- Use **Oxford commas** for clarity in lists.
- Always end descriptive lines with a **period**.
- For `@param`, `@return`, etc., write the description in sentence case.

```kotlin
@param timeout The time in milliseconds before the operation times out.
```

---

## 5. AI Agent Instructions

When generating or modifying Kotlin code with KDoc:

- **Ensure** documentation for `public`  and `internal` API exists.
- **Wrap** text at 90 characters.
- **Preserve** the structure defined above.
- **Correct grammar and punctuation**.
- **Infer tag content** when possible, based on function signature.
- **Avoid duplication** between summary and detailed description.
- **Omit boilerplate** such as “This function...” in summaries.
- **ALWAYS WRAP the modified Kotlin code at 100 characters** or `detekt` build error will occur. 

---

## References
- MIL-STD-961F: Department of Defense Standard Practice for Defense Specifications.
- Apple Human Interface Guidelines: [Typography](https://developer.apple.com/design/human-interface-guidelines/foundations/typography/)

---

End of guide.

