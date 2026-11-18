import { readFileSync } from "node:fs";

import { type APIResponse, expect, type Page } from "@playwright/test";

import { randomRuc } from "../pom/tenantAdminPages";
import { E2E_USERNAME } from "./auth";

/** Where `auth.setup.ts` leaves the ids of what it prepared, for the specs to read. */
export const FIXTURES_PATH = ".auth/fixtures.json";

/** What `auth.setup.ts` prepared through the API (see `ensureFixtures`). */
export interface IE2eFixtures {
  tenantId: string;
  /** A company on the SUNAT fake that issued a document to itself and received it. */
  companyId: string;
  /** The issued document's buyer link token (`access_url`), for the public portal. */
  publicDocumentToken?: string;
}

/** The fixtures `auth.setup.ts` wrote; undefined when it hasn't run (only the public project). */
export const readFixtures = (): IE2eFixtures | undefined => {
  try {
    return JSON.parse(readFileSync(FIXTURES_PATH, "utf8")) as IE2eFixtures;
  } catch {
    return undefined;
  }
};

/** The logged-in user's access token, from the app's OIDC session (sessionStorage). */
const accessToken = async (page: Page): Promise<string> => {
  const token = await page.evaluate(() => {
    for (const key of Object.keys(sessionStorage)) {
      if (key.startsWith("oidc.user:")) {
        const user = JSON.parse(sessionStorage.getItem(key) ?? "{}") as {
          access_token?: string;
        };
        return user.access_token;
      }
    }
    return undefined;
  });
  expect(token, "the login left no OIDC session").toBeTruthy();
  return token!;
};

/** The `sub` claim of a JWT (no verification: it only names the user to the admin API). */
const subject = (token: string): string =>
  (
    JSON.parse(
      Buffer.from(token.split(".")[1], "base64url").toString("utf8"),
    ) as { sub: string }
  ).sub;

const ok = async (response: APIResponse, what: string) => {
  if (!response.ok()) {
    throw new Error(
      `${what} failed: ${response.status()} ${await response.text()}`,
    );
  }
  return response;
};

/** Statuses a sign-up may answer when the user can't (or needn't) create another tenant. */
const SIGN_UP_REFUSALS = new Set([400, 403, 409, 422, 429]);

/**
 * Prepares, through the xhandler API, what the organization specs need on a stack that may be
 * fresh or may have outlived earlier runs, so they run instead of skipping:
 *
 * - an organization: signs the e2e user up (`POST /tenants`) when they belong to none;
 * - a company (a random RUC; every company starts on the SUNAT fake) when it has none;
 * - a factura the company issues to itself (the fake accepts it), whose buyer link is the
 *   public portal's, and whose XML the company then receives as a reception;
 * - a webhook, when the organization has none and its plan allows it;
 * - a partner the user belongs to, when they belong to none and are a platform admin.
 *
 * Writes the ids to `FIXTURES_PATH`.
 */
export const ensureFixtures = async (page: Page): Promise<IE2eFixtures> => {
  const token = await accessToken(page);
  const headers = { Authorization: `Bearer ${token}` };
  const api = "/api/xhandler/v1";
  const get = async <T>(path: string): Promise<T> =>
    (await (
      await ok(
        await page.request.get(`${api}${path}`, { headers }),
        `GET ${path}`,
      )
    ).json()) as T;
  const post = async <T>(
    path: string,
    data: unknown,
    extraHeaders: Record<string, string> = {},
  ): Promise<T> =>
    (await (
      await ok(
        await page.request.post(`${api}${path}`, {
          headers: { ...headers, ...extraHeaders },
          data,
        }),
        `POST ${path}`,
      )
    ).json()) as T;

  // The organization
  const myTenants = () => get<{ id: string }[]>("/tenants");
  if ((await myTenants()).length === 0) {
    const created = await page.request.post(`${api}/tenants`, {
      headers,
      data: { name: `E2E ${E2E_USERNAME}` },
    });
    if (!created.ok() && !SIGN_UP_REFUSALS.has(created.status())) {
      await ok(created, "Creating the e2e tenant");
    }
  }
  const tenants = await myTenants();
  expect(
    tenants.length,
    "the e2e user still belongs to no organization",
  ).toBeGreaterThan(0);
  const tenantId = tenants[0].id;

  // The company
  const companies = await get<{ id: string; ruc: string }[]>(
    `/tenants/${tenantId}/companies`,
  );
  const company =
    companies[0] ??
    (await (async () => {
      const ruc = randomRuc();
      return post<{ id: string; ruc: string }>(
        `/tenants/${tenantId}/companies`,
        { ruc, razon_social: `E2E ${ruc} SAC` },
      );
    })());

  // A factura to itself: a document for the lists, a buyer link, and a reception
  const issued = await post<{ id: string; access_url?: string }>(
    `/tenants/${tenantId}/companies/${company.id}/documents`,
    {
      serie: "F001",
      document: {
        kind: "Invoice",
        spec: {
          cliente: {
            tipo_documento_identidad: "6",
            numero_documento_identidad: company.ruc,
            nombre: "Cliente E2E SAC",
          },
          detalles: [{ descripcion: "Servicio E2E", cantidad: 1, precio: 100 }],
        },
      },
    },
    { "Idempotency-Key": crypto.randomUUID() },
  );
  const xml = await ok(
    await page.request.get(
      `${api}/tenants/${tenantId}/documents/${issued.id}/files/xml`,
      { headers },
    ),
    "GET the issued XML",
  );
  await ok(
    await page.request.post(
      `${api}/tenants/${tenantId}/companies/${company.id}/receptions`,
      {
        headers,
        multipart: {
          xml: {
            name: "factura.xml",
            mimeType: "application/xml",
            buffer: await xml.body(),
          },
        },
      },
    ),
    "POST a reception",
  );

  // A webhook (deliveries to it may fail: the specs only read the list)
  const webhooks = await get<unknown[]>(`/tenants/${tenantId}/webhooks`);
  if (webhooks.length === 0) {
    const created = await page.request.post(
      `${api}/tenants/${tenantId}/webhooks`,
      {
        headers,
        data: {
          url: "https://example.com/xhandler-e2e",
          events: ["document.accepted"],
        },
      },
    );
    // 402/409: a free plan without a verified RUC has no webhooks (its spec then skips).
    if (![402, 409].includes(created.status())) {
      await ok(created, "POST a webhook");
    }
  }

  // A partner (the admin API is for platform admins: anyone else's partner specs skip)
  const partners = await get<unknown[]>("/partners");
  if (partners.length === 0) {
    const created = await page.request.post("/api/xhandler/admin/v1/partners", {
      headers,
      data: { name: `Socio E2E ${E2E_USERNAME}` },
    });
    if (created.ok()) {
      const { id } = (await created.json()) as { id: string };
      await ok(
        await page.request.put(
          `/api/xhandler/admin/v1/partners/${id}/members`,
          {
            headers,
            data: { kind: "user", subject: subject(token), role: "admin" },
          },
        ),
        "PUT the partner member",
      );
    } else if (created.status() !== 403) {
      await ok(created, "POST a partner");
    }
  }

  return {
    tenantId,
    companyId: company.id,
    publicDocumentToken: issued.access_url?.split("/").pop(),
  };
};
