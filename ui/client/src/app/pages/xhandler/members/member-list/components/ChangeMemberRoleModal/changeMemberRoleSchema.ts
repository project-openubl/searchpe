import { mixed, object } from "yup";

import {
  ASSIGNABLE_MEMBER_ROLES,
  type AssignableMemberRole,
} from "@app/queries/xhandler/members";

/** `PUT …/members/{id}`: admin, operator or viewer (owners aren't granted here). */
export const changeMemberRoleSchema = object({
  role: mixed<AssignableMemberRole>()
    .required("Elige un rol")
    .oneOf([...ASSIGNABLE_MEMBER_ROLES], "Elige un rol"),
});
