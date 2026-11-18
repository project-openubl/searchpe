/** What the API accepts for a logo (company or partner): PNG or JPEG, ≤ 200 KB. */
export const LOGO_MAX_BYTES = 200 * 1024;

export const LOGO_TYPES = ["image/png", "image/jpeg"] as const;

/** The Spanish reason a file can't be a logo, or undefined when it can. */
export const logoFileProblem = (file: File | undefined): string | undefined => {
  if (!file) return "Elige una imagen";
  if (!(LOGO_TYPES as readonly string[]).includes(file.type)) {
    return "El logo debe ser una imagen PNG o JPEG";
  }
  if (file.size > LOGO_MAX_BYTES) return "El logo no debe pasar de 200 KB";
  return undefined;
};
