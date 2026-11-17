import { z } from "zod";

/** Fields as stores hold them: strings, or string lists for list fields. */
export type RawFields = Record<string, string | string[]>;

/** A raw write; `undefined` removes the field. */
export type RawWrite = Record<string, string | string[] | undefined>;

export type PersistentFieldValue =
  string | number | boolean | undefined | readonly (string | number)[];

/** A flat object schema; nested objects can't be flattened into query params. */
export type PersistentStateSchema = z.ZodObject<
  Record<string, z.ZodType<PersistentFieldValue>>
>;

type FieldRecord = Record<string, unknown>;

const toRawField = (value: unknown): string | string[] | undefined => {
  if (value === undefined || value === null) return undefined;
  if (Array.isArray(value)) return value.map(String);
  return String(value as string | number | boolean);
};

/** Any record (a parsed query, stored JSON, a typed value) → raw fields; empty values dropped. */
export const toRawFields = (source: FieldRecord): RawFields => {
  const fields: RawFields = {};
  for (const [name, value] of Object.entries(source)) {
    const raw = toRawField(value);
    if (raw !== undefined) fields[name] = raw;
  }
  return fields;
};

const isEqualField = (a: unknown, b: unknown): boolean =>
  Array.isArray(a) && Array.isArray(b)
    ? a.length === b.length && a.every((item, index) => item === b[index])
    : a === b;

/** Field-by-field equality, with lists compared by their items. */
export const isEqualValue = (a: FieldRecord, b: FieldRecord): boolean =>
  Object.keys({ ...a, ...b }).every((name) => isEqualField(a[name], b[name]));

interface IDecodeOptions<S extends PersistentStateSchema> {
  /** Raw fields, or any record of them (a parsed query): values are normalized first. */
  fields: FieldRecord;
  schema: S;
  defaultValue: z.output<S>;
}

/** A list field keeps its valid items, so one stale value doesn't empty the whole list. */
const parseField = (fieldSchema: z.ZodType, input: unknown) => {
  if (fieldSchema instanceof z.ZodArray && Array.isArray(input)) {
    const items = input.flatMap((item) => {
      const parsed = z.safeParse(fieldSchema.element, item);
      return parsed.success ? [parsed.data] : [];
    });
    return fieldSchema.safeParse(items);
  }
  return fieldSchema.safeParse(input);
};

/**
 * The present, valid fields, typed; a lone value for a list field (`?type=file`) becomes a
 * one-item list. The default only tells which fields are lists.
 */
export const parseFields = <S extends PersistentStateSchema>({
  fields,
  schema,
  defaultValue,
}: IDecodeOptions<S>): Partial<z.output<S>> => {
  const defaults: FieldRecord = defaultValue;
  const rawFields = toRawFields(fields);
  const value: FieldRecord = {};
  for (const [name, fieldSchema] of Object.entries(schema.shape)) {
    const raw = rawFields[name];
    if (raw === undefined) continue;
    const input =
      Array.isArray(defaults[name]) && !Array.isArray(raw) ? [raw] : raw;
    const parsed = parseField(fieldSchema, input);
    if (parsed.success) value[name] = parsed.data;
  }
  // Every key was parsed by its own field schema.
  return value as Partial<z.output<S>>;
};

/** Raw stored fields → typed value; a missing or invalid field falls back to its default. */
export const decode = <S extends PersistentStateSchema>(
  options: IDecodeOptions<S>,
): z.output<S> => ({ ...options.defaultValue, ...parseFields(options) });

/** Typed value → raw write. Fields equal to their default are removed, so defaults stay out of the URL. */
export const encode = <S extends PersistentStateSchema>({
  value,
  defaultValue,
}: {
  value: z.output<S>;
  defaultValue: z.output<S>;
}): RawWrite => {
  const fields: Record<string, PersistentFieldValue> = value;
  const defaults: Record<string, PersistentFieldValue> = defaultValue;
  const write: RawWrite = {};
  for (const name of Object.keys({ ...defaults, ...fields })) {
    write[name] = isEqualField(fields[name], defaults[name])
      ? undefined
      : toRawField(fields[name]);
  }
  return write;
};
