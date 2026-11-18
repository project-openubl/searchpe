import { object, string } from "yup";

/** Blank clears the address (`PUT …/notification-settings`). */
export const notificationEmailSchema = object({
  notification_email: string()
    .trim()
    .email("Ingresa un correo válido")
    .max(300, "Como máximo 300 caracteres"),
});
