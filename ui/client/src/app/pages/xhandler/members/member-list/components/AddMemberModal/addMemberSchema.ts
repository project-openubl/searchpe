import { type InferType, mixed, object, string } from "yup";

import {
  ASSIGNABLE_MEMBER_ROLES,
  type AssignableMemberRole,
  MEMBER_KINDS,
  type MemberKind,
} from "@app/queries/xhandler/members";

export const addMemberSchema = object({
  kind: mixed<MemberKind>()
    .required("Elige el tipo de miembro")
    .oneOf([...MEMBER_KINDS], "Elige el tipo de miembro"),
  subject: string()
    .trim()
    .required("Ingresa el identificador")
    .max(255, "Como máximo 255 caracteres"),
  role: mixed<AssignableMemberRole>()
    .required("Elige un rol")
    .oneOf([...ASSIGNABLE_MEMBER_ROLES], "Elige un rol"),
});

export const ADD_MEMBER_DEFAULTS: InferType<typeof addMemberSchema> = {
  kind: "user",
  subject: "",
  role: "viewer",
};
