/**
 * The `promptMetadata` JSON helpers.
 *
 * The column is a free-form `text` string; the worked example is two optional
 * keys inside it. These functions are the only place that reads or writes the
 * string, so the create form, the edit form and the detail block cannot drift
 * apart on what "an example is present" means.
 *
 * A pure module on purpose: no React import, so it is testable without a DOM
 * and a component file stays a file of components.
 */

export interface WorkedExample {
  exampleInput?: string;
  exampleOutput?: string;
}

function nonEmptyString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  return value.trim() ? value : undefined;
}

/**
 * Parses the stored string defensively, in the same try/catch shape the page
 * already uses for the playground: corrupt JSON yields no example rather than
 * throwing during render.
 */
export function readWorkedExample(promptMetadata?: string): WorkedExample {
  if (!promptMetadata) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(promptMetadata);
  } catch {
    return {};
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
  const record = parsed as Record<string, unknown>;
  return {
    exampleInput: nonEmptyString(record.exampleInput),
    exampleOutput: nonEmptyString(record.exampleOutput),
  };
}

/** The metadata object behind the string, or an empty object when it is not an object. */
export function parsePromptMetadata(promptMetadata?: string): Record<string, unknown> {
  if (!promptMetadata) return {};
  try {
    const parsed: unknown = JSON.parse(promptMetadata);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return { ...(parsed as Record<string, unknown>) };
  } catch {
    return {};
  }
}

/**
 * Serialises a prompt form's state back into `promptMetadata`, keeping every
 * key the form does not own — the create form builds the object, the edit form
 * merges into what was loaded, so `role`, `recommendedModel`, `temperature`,
 * `variables` and any future key survive an edit.
 *
 * A blank field removes its key instead of storing `""`: "no example" is the
 * absence of the key, matching how A5-2 reads it.
 */
export function withWorkedExample(
  base: Record<string, unknown>,
  exampleInput: string,
  exampleOutput: string,
): Record<string, unknown> {
  const next = { ...base };
  const input = exampleInput.trim();
  const output = exampleOutput.trim();
  if (input) {
    next.exampleInput = input;
  } else {
    delete next.exampleInput;
  }
  if (output) {
    next.exampleOutput = output;
  } else {
    delete next.exampleOutput;
  }
  return next;
}

/**
 * The edit form's whole write path for `promptMetadata`: merge the two example
 * keys into what was loaded and leave every other key alone.
 *
 * A stored value that is not valid JSON cannot be merged into. When the form
 * also has no example to add, it is handed back byte-for-byte rather than
 * replaced with `{}`; when there is an example, the new object is all a save
 * can honestly write.
 */
export function mergeWorkedExample(
  promptMetadata: string,
  exampleInput: string,
  exampleOutput: string,
): string {
  const base = parsePromptMetadata(promptMetadata);
  const unparseableAndUnchanged =
    Object.keys(base).length === 0
    && promptMetadata.trim() !== ''
    && !exampleInput.trim()
    && !exampleOutput.trim();
  if (unparseableAndUnchanged) return promptMetadata;
  return JSON.stringify(withWorkedExample(base, exampleInput, exampleOutput));
}
