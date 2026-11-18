import { array, boolean, object, string } from "yup";

export const preferencesSchema = object({
  preferences: array(
    object({
      kind: string().required(),
      email: boolean().required(),
      in_app: boolean().required(),
    }),
  ).required(),
});
